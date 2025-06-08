package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.SecretChatHelper.CURRENT_SECRET_CHAT_LAYER;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.secretgroups.action.AddMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.AllSecondaryChatsInitializedAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmGroupInitializationAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmJoinAction;
import org.telegram.messenger.partisan.secretgroups.action.CreateGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.CreateGroupForNewMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.DeleteAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.DeleteMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.EncryptedGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.GroupCreationFailedAction;
import org.telegram.messenger.partisan.secretgroups.action.NewAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class EncryptedGroupProtocol implements AccountControllersProvider {
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

    public void requestMembersToCreateSecondaryChats(EncryptedGroup encryptedGroup) {
        log(encryptedGroup, "Request members to create secondary chats.");
        encryptedGroup.setState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        sendActionToAllMembers(encryptedGroup, new ConfirmGroupInitializationAction());
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

    public void sendGroupCreationFailedToAllMembers(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.getOwnerUserId() != getUserConfig().getClientUserId()) {
            return;
        }
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            innerChat.setState(InnerEncryptedChatState.INITIALIZATION_FAILED);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        }
        sendActionToAllMembers(encryptedGroup, new GroupCreationFailedAction());
    }

    public void kickMember(EncryptedGroup encryptedGroup, long userId) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(userId);
        if (innerChat == null) {
            return;
        }
        DeleteMemberAction action = new DeleteMemberAction();
        action.userId = userId;
        sendActionToAllMembers(encryptedGroup, action, true);

        boolean needWaitForMessageReceiving = innerChat.isNotInState(InnerEncryptedChatState.CANCELLED)
                && innerChat.getDialogId().isPresent();
        if (needWaitForMessageReceiving) {
            waitForMessageReceivingByServer(innerChat.getDialogId().orElse(0L), () ->
                    finishKickingMember(encryptedGroup, userId)
            );
        } else {
            finishKickingMember(encryptedGroup, userId);
        }
    }

    private void waitForMessageReceivingByServer(long targetDialogId, Runnable action) {
        NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                long currentDialogId = (long)args[3];
                if (currentDialogId == targetDialogId) {
                    getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
                    action.run();
                }
            }
        };
        getNotificationCenter().addObserver(observer, NotificationCenter.messageReceivedByServer);
    }

    private void finishKickingMember(EncryptedGroup encryptedGroup, long userId) {
        removeMember(encryptedGroup, userId);
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)) {
            requestMembersToCreateSecondaryChats(encryptedGroup);
        }
    }

    public void removeMember(EncryptedGroup encryptedGroup, long userId) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(userId);
        if (innerChat == null) {
            return;
        }
        Integer encryptedChatId = innerChat.getEncryptedChatId().orElse(null);
        if (encryptedChatId != null) {
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
            if (encryptedChat != null) {
                getMessagesController().deleteDialog(DialogObject.makeEncryptedDialogId(encryptedChatId), 0, true);
                deleteInnerChat(encryptedGroup, userId);
                AndroidUtilities.runOnUIThread(() ->
                        getNotificationCenter().postNotificationName(
                                NotificationCenter.encryptedGroupMemberRemoved,
                                encryptedGroup.getInternalId(),
                                userId)
                );
            }
        } else {
            deleteInnerChat(encryptedGroup, userId);
        }
    }

    void deleteInnerChat(EncryptedGroup encryptedGroup, long userId) {
        encryptedGroup.removeInnerChatByUserId(userId);
        getMessagesStorage().deleteEncryptedGroupInnerChat(encryptedGroup.getInternalId(), userId);
        getEncryptedGroupUtils().updateEncryptedGroupLastMessage(encryptedGroup.getInternalId());
        getEncryptedGroupUtils().updateEncryptedGroupUnreadCount(encryptedGroup.getInternalId());
        getEncryptedGroupUtils().updateEncryptedGroupLastMessageDate(encryptedGroup.getInternalId());
    }

    public void sendNewAvatar(EncryptedGroup encryptedGroup) {
        sendNewAvatar(encryptedGroup, null);
    }

    public void sendNewAvatar(EncryptedGroup encryptedGroup, TLRPC.EncryptedChat encryptedChat) {
        if (encryptedGroup.getOwnerUserId() != getUserConfig().getClientUserId() || encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED)) {
            return;
        }
        NewAvatarAction action = new NewAvatarAction();
        action.avatarBytes = EncryptedGroupUtils.serializeAvatar(encryptedGroup);
        if (encryptedChat != null) {
            sendAction(encryptedChat, action);
        } else {
            sendActionToAllMembers(encryptedGroup, action, true);
        }
    }

    public void deleteAvatar(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.getOwnerUserId() != getUserConfig().getClientUserId() || encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED)) {
            return;
        }
        sendActionToAllMembers(encryptedGroup, new DeleteAvatarAction(), true);
    }

    public void sendAddMember(EncryptedGroup encryptedGroup, long userId) {
        log(encryptedGroup, "Send add member");
        AddMemberAction action = new AddMemberAction();
        action.userId = userId;
        sendActionToAllMembers(encryptedGroup, action, true);
    }

    public void sendNewMemberInvitation(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            throw new RuntimeException("The secret chat isn't initialized");
        }
        log(encryptedGroup, "Send new member invitation");
        CreateGroupForNewMemberAction action = new CreateGroupForNewMemberAction();
        action.externalGroupId = encryptedGroup.getExternalId();
        action.name = encryptedGroup.getName();
        action.ownerUserId = getUserConfig().getClientUserId();
        action.memberIds = encryptedGroup.getInnerUserIds();
        sendAction(encryptedChat, action);
    }

    public void sendActionToAllMembers(EncryptedGroup encryptedGroup, EncryptedGroupAction action) {
        sendActionToAllMembers(encryptedGroup, action, false);
    }

    public void sendActionToAllMembers(EncryptedGroup encryptedGroup, EncryptedGroupAction action, boolean updateInterface) {
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            Integer encryptedChatId = innerChat.getEncryptedChatId().orElse(null);
            if (encryptedChatId == null) {
                continue;
            }
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
            if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
                continue;
            }
            sendAction(encryptedChat, action, updateInterface);
        }
    }

    private void sendAction(TLRPC.EncryptedChat encryptedChat, EncryptedGroupAction action) {
        sendAction(encryptedChat, action, false);
    }

    private void sendAction(TLRPC.EncryptedChat encryptedChat, EncryptedGroupAction action, boolean updateInterface) {
        EncryptedGroupsServiceMessage reqSend = new EncryptedGroupsServiceMessage();
        TLRPC.Message message;

        reqSend.encryptedGroupAction = action;
        reqSend.action = new TLRPC.TL_decryptedMessageActionNotifyLayer(); // action shouldn't be null, so we put a meaningless action there
        reqSend.action.layer = CURRENT_SECRET_CHAT_LAYER;
        message = createSecretGroupServiceMessage(encryptedChat, action, accountNum);
        reqSend.random_id = message.random_id;

        if (updateInterface) {
            MessageObject newMsgObj = new MessageObject(accountNum, message, false, false);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj.wasJustSent = true;
            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            getMessagesController().updateInterfaceWithMessages(message.dialog_id, objArr, 0);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        }

        getSecretChatHelper().performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    private static TLRPC.TL_messageService createSecretGroupServiceMessage(TLRPC.EncryptedChat encryptedChat, EncryptedGroupAction action, int accountNum) {
        if (action == null) {
            throw new RuntimeException("createSecretGroupServiceMessage error: action was null");
        }
        AccountInstance accountInstance = AccountInstance.getInstance(accountNum);

        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = action;
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
        if (EncryptedGroupAction.isVisibleAction(action)) {
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

    @Override
    public int getAccountNum() {
        return accountNum;
    }

    private void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        getEncryptedGroupUtils().log(encryptedGroup, message);
    }

    private void log(@Nullable Long encryptedGroupExternalId, String message) {
        getEncryptedGroupUtils().log(encryptedGroupExternalId, message);
    }
}
