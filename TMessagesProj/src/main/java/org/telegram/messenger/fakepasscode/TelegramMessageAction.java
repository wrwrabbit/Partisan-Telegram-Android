package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.results.RemoveChatsResult;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TelegramMessageAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {
    public static class Entry {
        public Entry() {}

        public Entry(long userId, String text, boolean addGeolocation) {
            this.userId = userId;
            this.text = text;
            this.addGeolocation = addGeolocation;
            this.dialogDeleted = false;
        }

        public Entry copy() {
            Entry entry = new Entry(userId, text, addGeolocation);
            entry.dialogDeleted = dialogDeleted;
            return entry;
        }

        public long userId;
        public String text;
        public boolean addGeolocation;
        @JsonIgnore
        public boolean dialogDeleted = false;
        @JsonIgnore
        public boolean serverReceived = false;
    }

    public List<Entry> entries = new ArrayList<>();
    public static TelegramMessageAction activeAction = null;

    @JsonIgnore
    private final Set<Integer> oldMessageIds = new HashSet<>();

    @JsonIgnore
    public List<Entry> sentEntries = new ArrayList<>();

    @JsonIgnore
    private FakePasscode fakePasscode;

    public TelegramMessageAction() {
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (entries.isEmpty()) {
            return;
        }
        this.fakePasscode = fakePasscode;
        FakePasscodeMessages.hasUnDeletedMessages.clear();
        FakePasscodeMessages.saveMessages();

        getMessagesController().forceResetDialogs();

        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogDeletedByAction);
        for (Entry entry : entries) {
            sendMessage(entry);
        }

        FakePasscodeMessages.saveMessages();
        sentEntries = entries.stream()
                .map(e -> {
                    Entry entry = e.copy();
                    RemoveChatsResult removeChatResult = fakePasscode.actionsResult.getRemoveChatsResult(accountNum);
                    entry.dialogDeleted = removeChatResult != null && removeChatResult.isRemovedChat(e.userId);
                    return entry;
                })
                .collect(Collectors.toList());
        if (!sentEntries.isEmpty()) {
            fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        }
        SharedConfig.saveConfig();
    }

    private void sendMessage(Entry entry) {
        String geolocation = Utils.getLastLocationString();
        String text = entry.text;
        if (entry.addGeolocation) {
            text += geolocation;
        }
        SendMessagesHelper.allowReloadDialogsByMessage = false;
        activeAction = this;
        getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(text, entry.userId, null, null, null, false,
                null, null, null, true, 0, null, false));
        SendMessagesHelper.allowReloadDialogsByMessage = true;
        activeAction = null;
        entry.dialogDeleted = false;
    }

    public static void sosMessageSent(TLRPC.Message message) {
        if (activeAction == null) {
            return;
        }
        activeAction.messageSent(message);
    }

    private void messageSent(TLRPC.Message message) {
        oldMessageIds.add(message.id);
        fakePasscode.actionsResult.getOrCreateTelegramMessageResult(accountNum)
                .addMessage(message.dialog_id, message.id);
        TLRPC.Message prevMessage = null;
        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(message.dialog_id);
        if (dialog != null) {
            MessageObject messageObject = getMessagesController().dialogMessagesByIds.get(dialog.top_message);
            if (messageObject != null) {
                prevMessage = messageObject.messageOwner;
            }
        }
        FakePasscodeMessages.FakePasscodeMessage fakePasscodeMessage;
        fakePasscodeMessage = new FakePasscodeMessages.FakePasscodeMessage(message.message, message.date, prevMessage);
        FakePasscodeMessages.addFakePasscodeMessage(accountNum, message.dialog_id, fakePasscodeMessage);
        deleteMessage(message.dialog_id, message.id);
    }

    private void deleteMessage(long chatId, int messageId) {
        MessagesController controller = getMessagesController();
        ArrayList<Integer> messages = new ArrayList<>();
        messages.add(messageId);
        if (ChatObject.isChannel(chatId, accountNum) || ChatObject.isChannel(-chatId, accountNum)) {
            // messages in channels are always deleted for everyone
        } else {
            controller.deleteMessages(messages, null, null, chatId,
                    false, ChatActivity.MODE_DEFAULT, false, 0, null, 0, false, true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }

        if (id == NotificationCenter.messageReceivedByServer) {
            messageReceivedByServer(args);
        } else if (id == NotificationCenter.dialogDeletedByAction) {
            dialogDeletedByAction(args);
        }
    }

    private void messageReceivedByServer(Object[] args) {
        int oldId = (int) args[0];
        TLRPC.Message message = (TLRPC.Message) args[2];
        if (message == null || !oldMessageIds.contains(oldId)) {
            return;
        }
        fakePasscode.actionsResult.getOrCreateTelegramMessageResult(accountNum)
                .addMessage(message.dialog_id, message.id);
        deleteMessage(message.dialog_id, message.id);
        for (Entry entry : entries) {
            if (entry.userId == message.dialog_id) {
                entry.serverReceived = true;
                if (entry.dialogDeleted) {
                    AndroidUtilities.runOnUIThread(() -> Utils.deleteDialog(accountNum, entry.userId), 100);
                }
            }
        }
        if (entries.stream().allMatch(e -> e.serverReceived)) {
            Utilities.globalQueue.postRunnable(() -> fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this));
        }
    }

    private void dialogDeletedByAction(Object[] args) {
        sentEntries.stream()
                .filter(entry -> entry.userId == (long)args[0])
                .forEach(entry -> entry.dialogDeleted = true);
    }
}
