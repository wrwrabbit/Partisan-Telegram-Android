package org.telegram.messenger.partisan;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public class UserMessagesDeleter implements NotificationCenter.NotificationCenterDelegate {
    private final long userId;
    private final long dialogId;
    private final long topicId;
    @Nullable
    private final Predicate<MessageObject> condition;
    private final int accountNum;

    private final int deleteAllMessagesGuid;

    private int loadIndex = 1;
    private Long loadingTimeout = null;

    private static class MessagesToDelete {
        public ArrayList<Integer> messagesIds = new ArrayList<>();
        public ArrayList<Long> randoms = new ArrayList<>();
    }

    public UserMessagesDeleter(int accountNum,
                               long userId,
                               long dialogId,
                               long topicId,
                               @Nullable Predicate<MessageObject> condition) {
        this.userId = userId;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.condition = condition;
        this.accountNum = accountNum;
        this.deleteAllMessagesGuid = ConnectionsManager.generateClassGuid();
    }

    public void start() {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            startSearchingMessages();
            loadingTimeout = System.currentTimeMillis() + 10_000;
            startLoadingMessages();
        } else {
            startLoadingMessages();
        }
    }

    private void startSearchingMessages() {
        getNotificationCenter().addObserver(this, NotificationCenter.chatSearchResultsAvailableAll);
        searchMessages(0);
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
        if (id == NotificationCenter.messagesDidLoad) {
            if ((int) args[10] == deleteAllMessagesGuid) {
                ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[2];
                if (processLoadedMessages(messages)) {
                    loadNewMessagesIfNeeded(messages);
                }
            }
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            if ((Integer) args[0] == deleteAllMessagesGuid) {
                finishDeletion();
            }
        } else if (id == NotificationCenter.chatSearchResultsAvailableAll && Objects.equals(args[0], deleteAllMessagesGuid)) {
            if ((int)args[0] == deleteAllMessagesGuid) {
                ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
                if (processLoadedMessages(messages)) {
                    searchNewMessages(messages);
                }
            }
        }
    }

    private boolean processLoadedMessages(ArrayList<MessageObject> messages) {
        if (!messages.isEmpty()) {
            deleteMessages(getMessagesToDelete(messages));
            getNotificationCenter().postNotificationName(NotificationCenter.userMessagesDeleted, dialogId);
            return true;
        } else {
            finishDeletion();
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
                || messageObject.messageOwner instanceof TLRPC.TL_messageService
                || messageObject.messageText.toString().equals(LocaleController.getString(R.string.ActionMigrateFromGroup))) {
            return false;
        }

        if (!DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.Chat chat = getMessagesController().getChat(dialogId);
            needDeleteMessage = messageObject.canEditMessage(chat);
        } else {
            needDeleteMessage = messageObject.messageOwner.from_id != null && messageObject.messageOwner.from_id.user_id == userId;
        }

        if (condition != null) {
            needDeleteMessage = needDeleteMessage && condition.test(messageObject);
        }

        return needDeleteMessage;
    }

    private void deleteMessages(MessagesToDelete messagesToDelete) {
        if (messagesToDelete == null || messagesToDelete.messagesIds.isEmpty()) {
            return;
        }

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
        if (System.currentTimeMillis() < loadingTimeout) {
            int maxId = messages.stream().mapToInt(MessageObject::getId).max().orElse(0);
            int minDate = messages.stream().mapToInt(m -> m.messageOwner.date).min().orElse(0);
            loadMessages(maxId, minDate);
        } else {
            getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        }
    }

    private void loadMessages(int maxId, int minDate) {
        getMessagesController().loadMessages(dialogId, 0, false,
                100, maxId, 0, true, minDate,
                deleteAllMessagesGuid, DialogObject.isEncryptedDialog(dialogId) ? 2 : 0, 0,
                0, topicId, 0, loadIndex++, topicId != 0);
    }

    private void searchNewMessages(List<MessageObject> messages) {
        int maxId = messages.stream().mapToInt(MessageObject::getId).max().orElse(0);
        searchMessages(maxId);
    }

    private void searchMessages(int maxId) {
        getMediaDataController().searchMessagesInChat("", dialogId, 0, deleteAllMessagesGuid,
                0, 0, getMessagesController().getUser(userId),
                getMessagesController().getChat(dialogId), null, maxId);
    }

    private void finishDeletion() {
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatSearchResultsAvailableAll);
        getNotificationCenter().postNotificationName(NotificationCenter.userMessagesDeleted, dialogId);
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(accountNum);
    }

    private MediaDataController getMediaDataController() {
        return MediaDataController.getInstance(accountNum);
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(accountNum);
    }
}
