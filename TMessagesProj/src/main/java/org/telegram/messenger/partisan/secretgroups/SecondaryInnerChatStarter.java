package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils.log;

import android.content.Context;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

public class SecondaryInnerChatStarter {
    private final int accountNum;
    private final Context context;
    private final EncryptedGroup encryptedGroup;

    public SecondaryInnerChatStarter(int accountNum, Context context, EncryptedGroup encryptedGroup) {
        this.accountNum = accountNum;
        this.context = context;
        this.encryptedGroup = encryptedGroup;
    }

    public static void startSecondaryChats(int accountNum, Context context, EncryptedGroup encryptedGroup) {
        if (context == null) {
            return;
        }
        new SecondaryInnerChatStarter(accountNum, context, encryptedGroup).start();
    }

    public void start() {
        checkInnerEncryptedChats();
    }

    private void checkInnerEncryptedChats() {
        InnerEncryptedChat uninitializedInnerChat = encryptedGroup.getInnerChats().stream()
                .filter(c -> !c.getEncryptedChatId().isPresent() && c.getUserId() > getUserConfig().clientUserId) // Users with smaller ids will initialize chats with users with bigger ids.
                .findAny()
                .orElse(null);
        if (uninitializedInnerChat != null) {
            initializeNextEncryptedChat(uninitializedInnerChat);
        } else {
            EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
        }
    }

    private void initializeNextEncryptedChat(InnerEncryptedChat uninitializedInnerChat) {
        boolean isFirstChat = encryptedGroup.getInnerChats().stream()
                .noneMatch(c -> c.getEncryptedChatId().isPresent() && c.getUserId() > getUserConfig().clientUserId && c.getUserId() != encryptedGroup.getOwnerUserId());
        int delay = isFirstChat ? 0 : 10*1000;
        TLRPC.User user = getMessagesController().getUser(uninitializedInnerChat.getUserId());
        log(encryptedGroup, accountNum, "Start secondary inner chat with a user.");
        Utilities.globalQueue.postRunnable(
                () -> getSecretChatHelper().startSecretChat(context, user, this::onInternalEncryptedChatStarted),
                delay
        );
    }

    private void onInternalEncryptedChatStarted(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat != null) {
            log(encryptedGroup, accountNum, "Start secondary inner chat with a user.");
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
            innerChat.setEncryptedChatId(encryptedChat.id);
            innerChat.setState(InnerEncryptedChatState.NEED_SEND_SECONDARY_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

            checkInnerEncryptedChats();
        }
    }

    private UserConfig getUserConfig() {
        return UserConfig.getInstance(accountNum);
    }

    private MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(accountNum);
    }

    private MessagesController getMessagesController() {
        return MessagesController.getInstance(accountNum);
    }

    private SecretChatHelper getSecretChatHelper() {
        return SecretChatHelper.getInstance(accountNum);
    }
}
