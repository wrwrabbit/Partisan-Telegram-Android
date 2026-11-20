package org.telegram.messenger.partisan.secretsearch;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.InnerEncryptedChat;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Secret chat messages exist only in the local DB, so search scans them instead of asking the server.
// Nothing is indexed on purpose: an index would keep text of deleted messages on disk.
// Rows are read in batches on the storage queue and matched on the search queue, so a long scan
// never holds the storage queue for more than one batch. The scan stops after each page and resumes
// from where it stopped when the next page is requested.
public class SecretChatMessageSearcher {
    private static final int PAGE_SIZE = 50;
    private static final int ROWS_PER_DB_READ = 300;
    private static final AtomicInteger currentSearchId = new AtomicInteger();

    public static class Page {
        public final ArrayList<MessageObject> messages;
        public final boolean endReached;

        Page(ArrayList<MessageObject> messages, boolean endReached) {
            this.messages = messages;
            this.endReached = endReached;
        }
    }

    private final int account;
    private final long dialogId;
    private final String query;
    private final int searchId;
    private final MessageTextMatcher matcher;
    private final long selfId;
    private final boolean isEncryptedGroup;
    // For a secret group: every inner chat. For a secret chat: just the chat itself.
    private final Map<Long, Long> peerUserIdByDialogId;

    private final List<TLRPC.Message> pageMessages = new ArrayList<>();
    // Rows already read from the DB but not yet matched, because the previous page filled up first.
    private final Deque<TLRPC.Message> pendingMessages = new ArrayDeque<>();
    // An outgoing secret group message is stored once per member chat, all copies sharing random_id.
    private final Set<Long> foundOutgoingRandomIds = new HashSet<>();
    private long lastReadMid = Long.MIN_VALUE;
    private boolean allRowsRead;

    private boolean loading;
    private boolean endReached;
    private Consumer<Page> onPageOnUiThread;

    private SecretChatMessageSearcher(int account, long dialogId, String query, int searchId) {
        this.account = account;
        this.dialogId = dialogId;
        this.query = query;
        this.searchId = searchId;
        this.matcher = new MessageTextMatcher(query);
        this.selfId = UserConfig.getInstance(account).getClientUserId();
        MessagesController messagesController = MessagesController.getInstance(account);
        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroupByDialogId(dialogId);
        this.isEncryptedGroup = encryptedGroup != null;
        this.peerUserIdByDialogId = isEncryptedGroup
                ? getInnerChatPeers(encryptedGroup)
                : getSecretChatPeer(messagesController, dialogId);
    }

    private static Map<Long, Long> getInnerChatPeers(EncryptedGroup encryptedGroup) {
        Map<Long, Long> peers = new HashMap<>();
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            innerChat.getDialogId().ifPresent(innerDialogId -> peers.put(innerDialogId, innerChat.getUserId()));
        }
        return peers;
    }

    private static Map<Long, Long> getSecretChatPeer(MessagesController messagesController, long dialogId) {
        TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
        Map<Long, Long> peers = new HashMap<>();
        peers.put(dialogId, encryptedChat != null ? encryptedChat.user_id : 0L);
        return peers;
    }

    public static SecretChatMessageSearcher start(int account, long dialogId, String query, Consumer<Page> onFirstPageOnUiThread) {
        int searchId = currentSearchId.incrementAndGet();
        SecretChatMessageSearcher searcher = new SecretChatMessageSearcher(account, dialogId, query, searchId);
        if (searcher.matcher.isQueryEmpty() || searcher.peerUserIdByDialogId.isEmpty()) {
            searcher.endReached = true;
            AndroidUtilities.runOnUIThread(() -> onFirstPageOnUiThread.accept(new Page(new ArrayList<>(), true)));
            return searcher;
        }
        searcher.loadNextPage(onFirstPageOnUiThread);
        return searcher;
    }

    public boolean canLoadNextPage() {
        return !loading && !endReached && !isCancelled();
    }

    public void loadNextPage(Consumer<Page> onPageOnUiThread) {
        if (!canLoadNextPage()) {
            return;
        }
        loading = true;
        this.onPageOnUiThread = onPageOnUiThread;
        Utilities.searchQueue.postRunnable(() -> {
            if (isCancelled()) {
                return;
            }
            pageMessages.clear();
            continuePage();
        });
    }

    private boolean isCancelled() {
        return searchId != currentSearchId.get();
    }

    private void continuePage() {
        matchPendingMessages();
        if (pageMessages.size() >= PAGE_SIZE || allRowsRead && pendingMessages.isEmpty()) {
            deliverPage();
        } else {
            readNextBatch(lastReadMid);
        }
    }

    private void matchPendingMessages() {
        while (!pendingMessages.isEmpty() && pageMessages.size() < PAGE_SIZE) {
            TLRPC.Message message = pendingMessages.poll();
            if (matches(message) && !isHidden(message) && !isDuplicateOutgoingSecretGroupMessageCopy(message)) {
                pageMessages.add(message);
            }
        }
    }

    private void readNextBatch(long afterMid) {
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            if (isCancelled()) {
                return;
            }
            Batch batch = readBatch(afterMid);
            Utilities.searchQueue.postRunnable(() -> onBatchRead(batch));
        });
    }

    private void onBatchRead(Batch batch) {
        if (isCancelled()) {
            return;
        }
        lastReadMid = batch.lastMid;
        allRowsRead = batch.isLast();
        pendingMessages.addAll(batch.messages);
        continuePage();
    }

    private boolean isHidden(TLRPC.Message message) {
        return FakePasscodeUtils.isHideMessage(account, message.dialog_id, message.id)
                || isEncryptedGroup && FakePasscodeUtils.isHideMessage(account, dialogId, message.id);
    }

    private boolean isDuplicateOutgoingSecretGroupMessageCopy(TLRPC.Message message) {
        if (!isEncryptedGroup || !message.out || message.random_id == 0) {
            return false;
        }
        return !foundOutgoingRandomIds.add(message.random_id);
    }

    private void deliverPage() {
        ArrayList<MessageObject> messageObjects = pageMessages.stream()
                .map(this::createMessageObject)
                .collect(Collectors.toCollection(ArrayList::new));
        boolean pageEndReached = allRowsRead && pendingMessages.isEmpty();
        AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            endReached = pageEndReached;
            if (!isCancelled()) {
                onPageOnUiThread.accept(new Page(messageObjects, pageEndReached));
            }
        });
    }

    private Batch readBatch(long afterMid) {
        Batch batch = new Batch(afterMid);
        SQLiteCursor cursor = null;
        try {
            String dialogIds = TextUtils.join(",", peerUserIdByDialogId.keySet());
            cursor = MessagesStorage.getInstance(account).getDatabase().queryFinalized(
                    "SELECT data, mid, send_state, date, read_state, uid FROM messages_v2 WHERE uid IN (" + dialogIds + ") AND mid > ? ORDER BY mid ASC LIMIT " + ROWS_PER_DB_READ,
                    afterMid);
            while (cursor.next()) {
                batch.rowCount++;
                batch.lastMid = cursor.longValue(1);
                TLRPC.Message message = readMessageOrNull(cursor);
                if (message != null) {
                    batch.messages.add(message);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            batch.failed = true;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return batch;
    }

    // A row that fails to deserialize is skipped rather than ending the search.
    private TLRPC.Message readMessageOrNull(SQLiteCursor cursor) {
        NativeByteBuffer data = null;
        try {
            data = cursor.byteBufferValue(0);
            if (data == null) {
                return null;
            }
            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            if (message == null) {
                return null;
            }
            message.readAttachPath(data, selfId);
            message.id = cursor.intValue(1);
            message.send_state = cursor.intValue(2);
            message.date = cursor.intValue(3);
            MessageObject.setUnreadFlags(message, cursor.intValue(4));
            message.dialog_id = cursor.longValue(5);
            restoreSenderId(message);
            return message;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (data != null) {
                data.reuse();
            }
        }
    }

    // TL_message_secret stores from_id as int32, so 64-bit user ids come back truncated.
    private void restoreSenderId(TLRPC.Message message) {
        Long peerUserId = peerUserIdByDialogId.get(message.dialog_id);
        long senderId = message.out ? selfId : (peerUserId != null ? peerUserId : 0);
        if (senderId == 0) {
            return;
        }
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = senderId;
    }

    private boolean matches(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_messageService || message instanceof TLRPC.TL_messageEmpty) {
            return false;
        }
        if (matcher.matches(message.message)) {
            return true;
        }
        TLRPC.Document document = MessageObject.getDocument(message);
        return document != null && matcher.matches(FileLoader.getDocumentFileName(document));
    }

    private MessageObject createMessageObject(TLRPC.Message message) {
        MessageObject messageObject = new MessageObject(account, message, null, null, null, null, null, true, true, 0, false, false, false);
        if (messageObject.hasValidGroupId()) {
            messageObject.isPrimaryGroupMessage = true;
        }
        messageObject.setQuery(query, true);
        return messageObject;
    }

    private static class Batch {
        final List<TLRPC.Message> messages = new ArrayList<>();
        long lastMid;
        int rowCount;
        boolean failed;

        Batch(long afterMid) {
            lastMid = afterMid;
        }

        // A failed query can't be paged past, so the search ends with what it has.
        boolean isLast() {
            return failed || rowCount < ROWS_PER_DB_READ;
        }
    }
}
