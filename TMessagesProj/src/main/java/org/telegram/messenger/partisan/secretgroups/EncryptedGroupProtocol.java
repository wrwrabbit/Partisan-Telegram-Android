package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.SecretChatHelper.CURRENT_SECRET_CHAT_LAYER;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.secretgroups.action.AllSecondaryChatsInitializedAction;
import org.telegram.messenger.partisan.secretgroups.action.ChangeGroupInfoAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmGroupInitializationAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmJoinAction;
import org.telegram.messenger.partisan.secretgroups.action.CreateGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.EncryptedGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.GroupCreationFailedAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupProtocol {
    private final int accountNum;

    public EncryptedGroupProtocol(int accountNum) {
        this.accountNum = accountNum;
    }

    public void sendInvitation(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            throw new RuntimeException("The secret chat isn't initialized");
        }
        log(encryptedGroup, "Send invitation");
        CreateGroupAction action = new CreateGroupAction();
        action.externalGroupId = encryptedGroup.getExternalId();
        action.name = encryptedGroup.getName();
        action.ownerUserId = getUserConfig().getClientUserId();
        action.memberIds = encryptedGroup.getInnerUserIds();
        sendAction(encryptedChat, action);
    }

    public void sendJoinConfirmation(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new ConfirmJoinAction());
    }

    public void sendGroupInitializationConfirmation(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new ConfirmGroupInitializationAction());
    }

    public void sendSecondaryInnerChatInvitation(TLRPC.EncryptedChat encryptedChat, long externalGroupId) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            throw new RuntimeException("The secret chat isn't initialized");
        }
        log(externalGroupId, "Start secondary chat with a user");
        StartSecondaryInnerChatAction secondaryInnerChatAction = new StartSecondaryInnerChatAction();
        secondaryInnerChatAction.externalGroupId = externalGroupId;
        sendAction(encryptedChat, secondaryInnerChatAction);
    }

    public void sendAllSecondaryChatsInitialized(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new AllSecondaryChatsInitializedAction());
    }

    public void sendGroupInitializationFailed(TLRPC.EncryptedChat encryptedChat) {
        sendAction(encryptedChat, new GroupCreationFailedAction());
    }

    private void sendAction(TLRPC.EncryptedChat encryptedChat, EncryptedGroupAction action) {
        if (!SharedConfig.encryptedGroupsEnabled) {
            return;
        }
        EncryptedGroupsServiceMessage reqSend = new EncryptedGroupsServiceMessage();
        TLRPC.Message message;

        reqSend.encryptedGroupAction = action;
        reqSend.action = new TLRPC.TL_decryptedMessageActionNotifyLayer(); // action shouldn't be null, so we put a meaningless action there
        reqSend.action.layer = CURRENT_SECRET_CHAT_LAYER;
        message = createSecretGroupServiceMessage(encryptedChat, reqSend.action, accountNum);
        reqSend.random_id = message.random_id;

        getSecretChatHelper().performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendActionToAllMembers(EncryptedGroup encryptedGroup, EncryptedGroupAction action) {
        if (!SharedConfig.encryptedGroupsEnabled) {
            return;
        }
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            Integer encryptedChatId = innerChat.getEncryptedChatId().orElse(null);
            if (encryptedChatId == null) {
                continue;
            }
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
            if (encryptedChat == null) {
                continue;
            }
            sendAction(encryptedChat, action);
        }
    }

    private static TLRPC.TL_messageService createSecretGroupServiceMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.DecryptedMessageAction decryptedMessage, int accountNum) {
        if (decryptedMessage == null) {
            throw new RuntimeException("createSecretGroupServiceMessage error: decryptedMessage was null");
        }
        AccountInstance accountInstance = AccountInstance.getInstance(accountNum);

        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = decryptedMessage;
        newMsg.local_id = newMsg.id = accountInstance.getUserConfig().getNewMessageId();
        newMsg.from_id = new TLRPC.TL_peerUser();
        newMsg.from_id.user_id = accountInstance.getUserConfig().getClientUserId();
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        newMsg.peer_id = new TLRPC.TL_peerUser();
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        if (encryptedChat.participant_id == accountInstance.getUserConfig().getClientUserId()) {
            newMsg.peer_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.peer_id.user_id = encryptedChat.participant_id;
        }
        if (decryptedMessage instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || decryptedMessage instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
            newMsg.date = accountInstance.getConnectionsManager().getCurrentTime();
        } else {
            newMsg.date = 0;
        }
        newMsg.random_id = accountInstance.getSendMessagesHelper().getNextRandomId();
        accountInstance.getUserConfig().saveConfig(false);

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(newMsg);
        accountInstance.getMessagesStorage().putMessages(arr, false, true, true, 0, false, 0, 0);

        return newMsg;
    }

    public void processEncryptedChatUpdate(TLRPC.EncryptedChat encryptedChat) {
        if (!SharedConfig.encryptedGroupsEnabled) {
            return;
        }
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            return;
        }
        if (encryptedChat instanceof TLRPC.TL_encryptedChat) {
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
            if (innerChat.getState() == InnerEncryptedChatState.NEED_SEND_INVITATION) {
                sendInvitation(encryptedChat, encryptedGroup);
                innerChat.setState(InnerEncryptedChatState.INVITATION_SENT);
                getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        getInviteMessageForNonPtgUsers(), innerChat.getDialogId().get(),
                        null, null, null, true, new ArrayList<>(), null,
                        null, true, 0, null, false
                );
                SendMessagesHelper.allowReloadDialogsByMessage = false;
                SendMessagesHelper.getInstance(accountNum).sendMessage(params);
                SendMessagesHelper.allowReloadDialogsByMessage = true;
            } else if (innerChat.getState() == InnerEncryptedChatState.NEED_SEND_SECONDARY_INVITATION) {
                sendSecondaryInnerChatInvitation(encryptedChat, encryptedGroup.getExternalId());
                innerChat.setState(InnerEncryptedChatState.INITIALIZED);
                getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
                EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
            }
        } else if (encryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
            if (encryptedGroup.getState() == EncryptedGroupState.INITIALIZED) {
                if (encryptedChat.history_deleted) {
                    encryptedGroup.removeInnerChat(encryptedChat.id);
                    getMessagesStorage().deleteEncryptedGroupInnerChat(encryptedGroup.getInternalId(), encryptedChat.user_id);
                    EncryptedGroupUtils.updateEncryptedGroupLastMessage(encryptedGroup.getInternalId(), accountNum);
                    EncryptedGroupUtils.updateEncryptedGroupUnreadCount(encryptedGroup.getInternalId(), accountNum);
                    EncryptedGroupUtils.updateEncryptedGroupLastMessageDate(encryptedGroup.getInternalId(), accountNum);
                } else {
                    InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
                    innerChat.setState(InnerEncryptedChatState.CANCELLED);
                    getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
                }
                boolean allInnerChatsCancelled = encryptedGroup.getInnerChats().stream()
                        .allMatch(innerChat -> innerChat.getState() == InnerEncryptedChatState.CANCELLED);
                if (allInnerChatsCancelled) {
                    encryptedGroup.setState(EncryptedGroupState.CANCELLED);
                    getMessagesStorage().updateEncryptedGroup(encryptedGroup);
                    AndroidUtilities.runOnUIThread(() -> {
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
                    });
                }
            } else {
                encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
                getMessagesStorage().updateEncryptedGroup(encryptedGroup);
                AndroidUtilities.runOnUIThread(() -> {
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
                });
                sendGroupCreationFailedToAllMembers(encryptedGroup);
            }
        }
    }

    private String getInviteMessageForNonPtgUsers() {
        String ruText = "Если Вы видите это сообщение, у Вас не установлен Партизанский Телеграм. " +
                "Для вступления в секретную группу скачайте приложение из официального канала: https://t.me/cpartisans_security\n\n" +
                "Если Вы уже используете Партизанский Телеграм и видите это сообщение, убедитесь в том, что:\n" +
                "- Секретный чат создался именно в нём;\n" +
                "- Приложение обновлено до последней версии;\n" +
                "- Ложный код-пароль не был активирован в момент создания секретной группы;\n" +
                "- Вы включили секретные группы в партизанских настройках.";
        String enText = "If you see this message, you do not have Partisan Telegram installed. " +
                "To join the secret group, download the application from the official channel: https://t.me/cpartisans_security\n\n" +
                "If you are already using Partisan Telegram and see this message, make sure that:\n" +
                "- The secret chat was created in it;\n" +
                "- The application has been updated to the latest version;\n" +
                "- The fake passcode was not activated when the secret group was created;\n" +
                "- You enabled secret groups in the partisan settings.";
        return ruText + "\n\n\n\n" + enText;
    }

    public void sendGroupCreationFailedToAllMembers(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.getOwnerUserId() != getUserConfig().getClientUserId()) {
            return;
        }
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            innerChat.setState(InnerEncryptedChatState.INITIALIZATION_FAILED);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
            if (!innerChat.getEncryptedChatId().isPresent()) {
                continue;
            }
            int encryptedChatId = innerChat.getEncryptedChatId().get();
            TLRPC.EncryptedChat innerEncryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
            if (innerEncryptedChat != null) {
                sendGroupInitializationFailed(innerEncryptedChat);
            }
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

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(accountNum);
    }

    private SecretChatHelper getSecretChatHelper() {
        return SecretChatHelper.getInstance(accountNum);
    }

    private void log(String message) {
        EncryptedGroupUtils.log(accountNum, message);
    }

    private void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        EncryptedGroupUtils.log(encryptedGroup, accountNum, message);
    }

    private void log(@Nullable Long encryptedGroupExternalId, String message) {
        EncryptedGroupUtils.log(encryptedGroupExternalId, accountNum, message);
    }

    private EncryptedGroup getEncryptedGroupByEncryptedChat(TLRPC.EncryptedChat encryptedChat) {
        return EncryptedGroupUtils.getEncryptedGroupByEncryptedChat(encryptedChat, accountNum);
    }
}
