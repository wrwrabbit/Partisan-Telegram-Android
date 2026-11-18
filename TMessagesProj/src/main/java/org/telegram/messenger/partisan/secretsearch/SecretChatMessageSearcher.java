package org.telegram.messenger.partisan.secretsearch;

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
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Secret chat messages exist only in the local DB, so search scans them instead of asking the server.
// Nothing is indexed on purpose: an index would keep text of deleted messages on disk.
// Rows are read in batches on the storage queue and matched on the search queue, so a long scan
// never holds the storage queue for more than one batch.
public class SecretChatMessageSearcher {
    private static final int MAX_RESULTS = 500;
    private static final int BATCH_SIZE = 300;
    private static final AtomicInteger currentSearchId = new AtomicInteger();

    private final int account;
    private final long dialogId;
    private final String query;
    private final int searchId;
    private final Consumer<ArrayList<MessageObject>> onResultsOnUiThread;
    private final MessageTextMatcher matcher;
    private final List<TLRPC.Message> foundMessages = new ArrayList<>();
    private final long selfId;
    private final long peerUserId;

    private SecretChatMessageSearcher(int account, long dialogId, String query, int searchId, Consumer<ArrayList<MessageObject>> onResultsOnUiThread) {
        this.account = account;
        this.dialogId = dialogId;
        this.query = query;
        this.searchId = searchId;
        this.onResultsOnUiThread = onResultsOnUiThread;
        this.matcher = new MessageTextMatcher(query);
        this.selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(account).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
        this.peerUserId = encryptedChat != null ? encryptedChat.user_id : 0;
    }

    // A newer search cancels the running one; a cancelled search never calls onResults.
    public static void search(int account, long dialogId, String query, Consumer<ArrayList<MessageObject>> onResultsOnUiThread) {
        int searchId = currentSearchId.incrementAndGet();
        SecretChatMessageSearcher searcher = new SecretChatMessageSearcher(account, dialogId, query, searchId, onResultsOnUiThread);
        if (searcher.matcher.isQueryEmpty() || MessagesController.getInstance(account).isEncryptedGroup(dialogId)) {
            AndroidUtilities.runOnUIThread(() -> onResultsOnUiThread.accept(new ArrayList<>()));
            return;
        }
        searcher.readNextBatch(Long.MIN_VALUE);
    }

    private boolean isCancelled() {
        return searchId != currentSearchId.get();
    }

    private void readNextBatch(long afterMid) {
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            if (isCancelled()) {
                return;
            }
            Batch batch = readBatch(afterMid);
            Utilities.searchQueue.postRunnable(() -> matchBatch(batch));
        });
    }

    private void matchBatch(Batch batch) {
        if (isCancelled()) {
            return;
        }
        for (TLRPC.Message message : batch.messages) {
            if (foundMessages.size() >= MAX_RESULTS) {
                break;
            }
            if (matches(message) && !FakePasscodeUtils.isHideMessage(account, dialogId, message.id)) {
                foundMessages.add(message);
            }
        }
        if (batch.isLast() || foundMessages.size() >= MAX_RESULTS) {
            deliverResults();
        } else {
            readNextBatch(batch.lastMid);
        }
    }

    private void deliverResults() {
        ArrayList<MessageObject> messageObjects = foundMessages.stream()
                .map(this::createMessageObject)
                .collect(Collectors.toCollection(ArrayList::new));
        AndroidUtilities.runOnUIThread(() -> {
            if (!isCancelled()) {
                onResultsOnUiThread.accept(messageObjects);
            }
        });
    }

    // Secret chat mids are negative and decrease over time, so ascending mid order is newest first.
    private Batch readBatch(long afterMid) {
        Batch batch = new Batch(afterMid);
        SQLiteCursor cursor = null;
        try {
            cursor = MessagesStorage.getInstance(account).getDatabase().queryFinalized(
                    "SELECT data, mid, send_state, date, read_state FROM messages_v2 WHERE uid = ? AND mid > ? ORDER BY mid ASC LIMIT " + BATCH_SIZE,
                    dialogId, afterMid);
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
            message.dialog_id = dialogId;
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
        long senderId = message.out ? selfId : peerUserId;
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
            return failed || rowCount < BATCH_SIZE;
        }
    }
}
