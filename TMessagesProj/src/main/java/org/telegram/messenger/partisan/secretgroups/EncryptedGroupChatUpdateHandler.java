package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class EncryptedGroupChatUpdateHandler implements AccountControllersProvider {
    private final int accountNum;

    public EncryptedGroupChatUpdateHandler(int accountNum) {
        this.accountNum = accountNum;
    }

    public void processEncryptedChatUpdate(TLRPC.EncryptedChat encryptedChat) {
        EncryptedGroup encryptedGroup = getEncryptedGroupUtils().getOrLoadEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            return;
        }
        if (encryptedChat instanceof TLRPC.TL_encryptedChat) {
            handleEncryptedChatCreated(encryptedGroup, encryptedChat);
        } else if (encryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
            handleEncryptedChatDiscarded(encryptedGroup, encryptedChat);
        }
    }

    private void handleEncryptedChatCreated(EncryptedGroup encryptedGroup, TLRPC.EncryptedChat encryptedChat) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
        if (innerChat.isInState(InnerEncryptedChatState.NEED_SEND_INVITATION, InnerEncryptedChatState.NEW_MEMBER_NEED_SEND_INVITATION)) {
            onPrimaryChatCreated(encryptedGroup, innerChat, encryptedChat);
        } else if (innerChat.isInState(InnerEncryptedChatState.NEED_SEND_SECONDARY_INVITATION)) {
            onSecondaryChatCreated(encryptedGroup, innerChat, encryptedChat);
        }
    }

    private void onPrimaryChatCreated(EncryptedGroup encryptedGroup, InnerEncryptedChat innerChat, TLRPC.EncryptedChat encryptedChat) {
        if (innerChat.isInState(InnerEncryptedChatState.NEED_SEND_INVITATION)) {
            getEncryptedGroupProtocol().sendInvitation(encryptedChat, encryptedGroup);
            innerChat.setState(InnerEncryptedChatState.INVITATION_SENT);
        } else if (innerChat.isInState(InnerEncryptedChatState.NEW_MEMBER_NEED_SEND_INVITATION)) {
            getEncryptedGroupProtocol().sendNewMemberInvitation(encryptedChat, encryptedGroup);
            innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_INVITATION_SENT);
        }
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                getInviteMessageForNonPtgUsers(), innerChat.getDialogId().get(),
                null, null, null, true, new ArrayList<>(), null,
                null, true, 0, null, false
        );
        SendMessagesHelper.allowReloadDialogsByMessage = false;
        SendMessagesHelper.getInstance(accountNum).sendMessage(params);
        SendMessagesHelper.allowReloadDialogsByMessage = true;
    }

    private String getInviteMessageForNonPtgUsers() {
        String ruText = "Если Вы видите это сообщение, у Вас не установлен Партизанский Телеграм. " +
                "Для вступления в секретную группу скачайте приложение из официального канала: https://t.me/cpartisans_security\n\n" +
                "Если Вы уже используете Партизанский Телеграм и видите это сообщение, убедитесь в том, что:\n" +
                "- Секретный чат создался именно в нём;\n" +
                "- Приложение обновлено до последней версии;\n" +
                "- Ложный код-пароль не был активирован в момент создания секретной группы.";
        String enText = "If you see this message, you do not have Partisan Telegram installed. " +
                "To join the secret group, download the application from the official channel: https://t.me/cpartisans_security\n\n" +
                "If you are already using Partisan Telegram and see this message, make sure that:\n" +
                "- The secret chat was created in it;\n" +
                "- The application has been updated to the latest version;\n" +
                "- The fake passcode was not activated when the secret group was created.";
        return ruText + "\n\n\n\n" + enText;
    }

    private void onSecondaryChatCreated(EncryptedGroup encryptedGroup, InnerEncryptedChat innerChat, TLRPC.EncryptedChat encryptedChat) {
        if (encryptedGroup.isNotInState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION, EncryptedGroupState.NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION)) {
            log(encryptedGroup, "Invalid encrypted group state during secondary chat creation: " + encryptedGroup.getState());
            return;
        }
        getEncryptedGroupProtocol().sendSecondaryInnerChatInvitation(encryptedChat, encryptedGroup.getExternalId());
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        getEncryptedGroupUtils().finalizeEncryptedGroupIfAllChatsCreated(encryptedGroup);
    }

    private void handleEncryptedChatDiscarded(EncryptedGroup encryptedGroup, TLRPC.EncryptedChat encryptedChat) {
        if (encryptedGroup.isInState(EncryptedGroupState.INITIALIZED)) {
            onInitializedChatDiscarded(encryptedGroup, encryptedChat);
        } else {
            onNonInitializedChatDiscarded(encryptedGroup);
        }
    }

    private void onInitializedChatDiscarded(EncryptedGroup encryptedGroup, TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat.history_deleted) {
            getEncryptedGroupProtocol().deleteInnerChat(encryptedGroup, encryptedChat.user_id);
        } else {
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
            innerChat.setState(InnerEncryptedChatState.CANCELLED);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        }
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.CANCELLED)) {
            encryptedGroup.setState(EncryptedGroupState.CANCELLED);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            });
        }
    }

    private void onNonInitializedChatDiscarded(EncryptedGroup encryptedGroup) {
        encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        getEncryptedGroupProtocol().sendGroupCreationFailedToAllMembers(encryptedGroup);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }

    private void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        getEncryptedGroupUtils().log(encryptedGroup, message);
    }
}
