package org.telegram.messenger.partisan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.TesterSettingsActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class UserMessagesDeleter implements NotificationCenter.NotificationCenterDelegate, AccountControllersProvider {
    private final long dialogId;
    private final long topicId;
    @Nullable
    private final Predicate<MessageObject> condition;
    private final int accountNum;

    private final int deleteAllMessagesGuid;

    private int loadIndex = 1;
    private Long loadingTimeout = null;
    private int searchOffsetId;
    private List<TLRPC.Peer> possibleSenderPeers;

    private static class MinMaxMessageIds {
        private int minMessageId = Integer.MAX_VALUE;
        private int maxMessageId = Integer.MIN_VALUE;

        public boolean compareAndUpdateValuesIfNeeded(int currentMinId, int currentMaxId) {
            if (currentMinId >= minMessageId && currentMaxId <= maxMessageId) {
                return false;
            } else {
                minMessageId = Math.min(minMessageId, currentMinId);
                maxMessageId = Math.max(maxMessageId, currentMaxId);
                return true;
            }
        }
    }

    private MinMaxMessageIds minMaxLoadedIds;
    private MinMaxMessageIds minMaxSearchedIds;

    private static class MessagesToDelete {
        public ArrayList<Integer> messagesIds = new ArrayList<>();
        public ArrayList<Long> randoms = new ArrayList<>();
    }

    public UserMessagesDeleter(int accountNum,
                               long dialogId,
                               long topicId,
                               @Nullable Predicate<MessageObject> condition) {
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.condition = condition;
        this.accountNum = accountNum;
        this.deleteAllMessagesGuid = ConnectionsManager.generateClassGuid();
    }

    public void start() {
        minMaxLoadedIds = new MinMaxMessageIds();
        minMaxSearchedIds = new MinMaxMessageIds();
        searchOffsetId = 0;
        possibleSenderPeers = getPossibleSenderPeers();

        if (onlyLoadMessages()) {
            log("start only load chat messages deletion");
            startLoadingMessages();
        } else {
            log("start load + search chat messages deletion");
            searchMessages();
            if (!TesterSettingsActivity.forceSearchDuringDeletion) {
                loadingTimeout = System.currentTimeMillis() + 10_000;
                startLoadingMessages();
            }
        }
    }

    private List<TLRPC.Peer> getPossibleSenderPeers() {
        List<TLRPC.Peer> peers = new ArrayList<>();
        TLRPC.Peer selfPeer = new TLRPC.TL_peerUser();
        selfPeer.user_id = getUserConfig().clientUserId;
        peers.add(selfPeer);
        TLRPC.TL_channels_sendAsPeers sendAsPeers = getMessagesController().getSendAsPeers(dialogId);
        if (sendAsPeers != null) {
            for (TLRPC.TL_sendAsPeer peer : sendAsPeers.peers) {
                if (peer.peer.channel_id != -dialogId && peer.peer.chat_id != -dialogId) {
                    peers.add(peer.peer);
                }
            }
        }
        return peers;
    }

    private void startLoadingMessages() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.loadingMessagesFailed);
        loadMessages(0, 0);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (args == null) {
            return;
        }
        log("didReceivedNotification " + id);
        if (id == NotificationCenter.messagesDidLoad) {
            if ((int) args[10] == deleteAllMessagesGuid) {
                if (!onlyLoadMessages() && TesterSettingsActivity.forceSearchDuringDeletion) {
                    return;
                }
                ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[2];
                log("messagesDidLoad:  " + messages.size());
                if (processLoadedMessages(messages, minMaxLoadedIds)) {
                    loadNewMessagesIfNeeded(messages);
                } else if (onlyLoadMessages()) {
                    finishDeletion();
                }
            }
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            if ((Integer) args[0] == deleteAllMessagesGuid) {
                log("loadingMessagesFailed");
                finishDeletion();
            }
        }
    }

    private boolean processLoadedMessages(List<MessageObject> messages, MinMaxMessageIds minMaxMessageIds) {
        log("processLoadedMessages: " + messages.size());
        if (!messages.isEmpty()) {
            int currentMinId = messages.stream().mapToInt(MessageObject::getId).min().orElse(Integer.MAX_VALUE);
            int currentMaxId = messages.stream().mapToInt(MessageObject::getId).max().orElse(Integer.MIN_VALUE);
            if (!minMaxMessageIds.compareAndUpdateValuesIfNeeded(currentMinId, currentMaxId)) {
                log("processLoadedMessages: no new messages loaded");
                return false;
            } else {
                deleteMessages(getMessagesToDelete(messages));
                getNotificationCenter().postNotificationName(NotificationCenter.userMessagesDeleted, dialogId);
                return true;
            }
        } else {
            log("processLoadedMessages: empty");
            return false;
        }
    }

    private MessagesToDelete getMessagesToDelete(List<MessageObject> messages) {
        MessagesToDelete messagesToDelete = new MessagesToDelete();
        for (MessageObject messageObject : messages) {
            if (!isNeedDeleteMessage(messageObject)) {
                continue;
            }
            messagesToDelete.messagesIds.add(messageObject.getId());
            if (DialogObject.isEncryptedDialog(dialogId)) {
                messagesToDelete.randoms.add(messageObject.messageOwner.random_id);
            }
        }
        return messagesToDelete;
    }

    private boolean isNeedDeleteMessage(MessageObject messageObject) {
        boolean needDeleteMessage;

        if (messageObject == null || messageObject.getDialogId() != dialogId || messageObject.messageOwner == null
                || messageObject.messageText.toString().equals(LocaleController.getString(R.string.ActionMigrateFromGroup))) {
            log("isNeedDeleteMessage main filter");
            return false;
        }

        TLRPC.Peer senderPeer = messageObject.messageOwner.from_id;
        needDeleteMessage = possibleSenderPeers.stream().anyMatch(currentPeer -> peersEqual(currentPeer, senderPeer));
        if (!needDeleteMessage) {
            log("isNeedDeleteMessage wrong user_id");
        }

        if (needDeleteMessage && condition != null) {
            needDeleteMessage = condition.test(messageObject);
            if (!needDeleteMessage) {
                log("isNeedDeleteMessage condition failed");
            }
        }

        return needDeleteMessage;
    }

    private static boolean peersEqual(TLRPC.Peer peer1, TLRPC.Peer peer2) {
        if (peer1 == null || peer2 == null) {
            return false;
        }
        return peer1.user_id == peer2.user_id
                && peer1.chat_id == peer2.chat_id
                && peer1.channel_id == peer2.channel_id;
    }

    private void deleteMessages(MessagesToDelete messagesToDelete) {
        if (messagesToDelete == null || messagesToDelete.messagesIds.isEmpty()) {
            log("deleteMessages: was null or empty");
            return;
        }
        log("deleteMessages: " + messagesToDelete.messagesIds.size() + " " + messagesToDelete.randoms.size());

        boolean isEncryptedDialog = DialogObject.isEncryptedDialog(dialogId);
        ArrayList<Long> randoms = isEncryptedDialog ? messagesToDelete.randoms : null;
        TLRPC.EncryptedChat encryptedChat = isEncryptedDialog
                ? getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))
                : null;
        boolean forAll = !isEncryptedDialog;
        boolean useQueue = isEncryptedDialog;
        boolean reset = !isEncryptedDialog;

        getMessagesController().deleteMessages(messagesToDelete.messagesIds, randoms, encryptedChat, dialogId,
                forAll, ChatActivity.MODE_DEFAULT, false, 0,
                null, (int)topicId, useQueue, reset);
    }

    private void loadNewMessagesIfNeeded(List<MessageObject> messages) {
        if (loadingTimeout == null || System.currentTimeMillis() < loadingTimeout) {
            int maxId = messages.stream().mapToInt(MessageObject::getId).max().orElse(0);
            int minDate = messages.stream().mapToInt(m -> m.messageOwner.date).min().orElse(0);
            log("loadNewMessagesIfNeeded: maxId = " + maxId);
            loadMessages(maxId, minDate);
        } else {
            log("loadNewMessagesIfNeeded: timeout");
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        }
    }

    private void loadMessages(int maxId, int minDate) {
        if (!onlyLoadMessages() && TesterSettingsActivity.forceSearchDuringDeletion) {
            return;
        }
        log("load messages. maxId = " + maxId + ", minDate = " + minDate);
        getMessagesController().loadMessages(dialogId, 0, false,
                100, maxId, 0, true, minDate,
                deleteAllMessagesGuid, DialogObject.isEncryptedDialog(dialogId) ? 2 : 0, 0,
                0, topicId, 0, loadIndex++, topicId != 0);
    }

    private void searchMessages() {
        log("search messages. searchOffsetId = " + searchOffsetId);
        TLRPC.TL_messages_search req = createSearchRequest();
        if (req == null) {
            return;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    onSearchResultAvailable(res.messages);
                });
            } else {
                handleSearchError(error);
            }
        });
    }

    private TLRPC.TL_messages_search createSearchRequest() {
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(dialogId);
        if (req.peer == null) {
            return null;
        }
        req.limit = 100;
        req.q = "";
        req.offset_id = searchOffsetId;
        TLRPC.User user = getMessagesController().getUser(getUserConfig().clientUserId);
        TLRPC.Chat chat = getMessagesController().getChat(dialogId);
        if (user != null) {
            req.from_id = MessagesController.getInputPeer(user);
            req.flags |= 1;
        } else if (chat != null) {
            req.from_id = MessagesController.getInputPeer(chat);
            req.flags |= 1;
        }
        if (topicId != 0) {
            req.top_msg_id = (int) topicId;
            req.flags |= 2;
        }
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        return req;
    }

    private void handleSearchError(TLRPC.TL_error error) {
        if (error.text.startsWith("FLOOD_WAIT")) {
            int floodWait = Utilities.parseInt(error.text);
            long delayMs = (floodWait + 1) * 1000L;
            AndroidUtilities.runOnUIThread(this::searchMessages, delayMs);
        } else {
            log("Unknown search error: " + error.text);
            finishDeletion();
        }
    }

    private void onSearchResultAvailable(List<TLRPC.Message> messages) {
        List<MessageObject> messageObjects = initializeMessageObjects(messages);
        log("onSearchResultAvailable:  " + messageObjects.size());
        if (processLoadedMessages(messageObjects, minMaxSearchedIds)) {
            searchOffsetId = messageObjects.stream().mapToInt(MessageObject::getId).min().orElse(0);
            searchMessages();
        } else {
            finishDeletion();
        }
    }

    private List<MessageObject> initializeMessageObjects(List<TLRPC.Message> messages) {
        return messages.stream()
                .map(m -> new MessageObject(accountNum, m, null, null, null, null, null, true, true, 0, false, false, false))
                .collect(Collectors.toList());
    }

    boolean onlyLoadMessages() {
        return DialogObject.isEncryptedDialog(dialogId);
    }

    private void finishDeletion() {
        log("deletion finished");
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        getNotificationCenter().postNotificationName(NotificationCenter.userMessagesDeleted, dialogId);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }

    private void log(String message) {
        PartisanLog.d("UserMessagesDeleter(" + hashCode() + "): " + message);
    }
}
