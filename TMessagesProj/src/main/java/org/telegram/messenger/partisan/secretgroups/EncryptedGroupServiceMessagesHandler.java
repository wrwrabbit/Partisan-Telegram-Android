package org.telegram.messenger.partisan.secretgroups;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.secretgroups.action.AllSecondaryChatsInitializedAction;
import org.telegram.messenger.partisan.secretgroups.action.ChangeGroupInfoAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmGroupInitializationAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmJoinAction;
import org.telegram.messenger.partisan.secretgroups.action.CreateGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.DeleteMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.EncryptedGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.GroupCreationFailedAction;
import org.telegram.messenger.partisan.secretgroups.action.NewAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupServiceMessagesHandler implements AccountControllersProvider {
    private final int accountNum;

    public EncryptedGroupServiceMessagesHandler(int accountNum) {
        this.accountNum = accountNum;
    }

    public void handleServiceMessage(TLRPC.EncryptedChat encryptedChat, EncryptedGroupsServiceMessage serviceMessage) {
        if (!SharedConfig.encryptedGroupsEnabled) {
            return;
        }
        EncryptedGroupAction action = serviceMessage.encryptedGroupAction;
        log("Handle service message " + action.getClass());
        if (action instanceof CreateGroupAction) {
            handleGroupCreation(encryptedChat, (CreateGroupAction) action);
        } else if (action instanceof ConfirmJoinAction) {
            handleConfirmJoinAction(encryptedChat, (ConfirmJoinAction) action);
        } else if (action instanceof ConfirmGroupInitializationAction) {
            handleConfirmGroupInitialization(encryptedChat, (ConfirmGroupInitializationAction) action);
        } else if (action instanceof StartSecondaryInnerChatAction) {
            handleStartSecondaryChat(encryptedChat, (StartSecondaryInnerChatAction) action);
        } else if (action instanceof AllSecondaryChatsInitializedAction) {
            handleAllSecondaryChatsInitialized(encryptedChat, (AllSecondaryChatsInitializedAction) action);
        } else if (action instanceof GroupCreationFailedAction) {
            handleGroupCreationFailed(encryptedChat, (GroupCreationFailedAction) action);
        } else if (action instanceof ChangeGroupInfoAction) {
            handleChangeGroupInfoAction(encryptedChat, (ChangeGroupInfoAction) action);
        } else if (action instanceof DeleteMemberAction) {
            handleDeleteMemberAction(encryptedChat, (DeleteMemberAction) action);
        } else if (action instanceof NewAvatarAction) {
            handleNewAvatar(encryptedChat, (NewAvatarAction)action);
        }
    }

    private void handleGroupCreation(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return;
        }
        EncryptedGroup encryptedGroup = createEncryptedGroup(encryptedChat, action);
        if (encryptedGroup == null) {
            return;
        }
        log(encryptedGroup, "Created.");

        forceHidePreview(encryptedChat, encryptedGroup);
        for (int i = 1; i <= 20; i++) {
            AndroidUtilities.runOnUIThread(() -> forceHidePreview(encryptedChat, encryptedGroup));
        }

        TLRPC.Dialog dialog = createTlrpcDialog(encryptedGroup);
        getMessagesController().dialogs_dict.put(dialog.id, dialog);
        getMessagesController().addDialog(dialog);
        getMessagesController().sortDialogs(null);

        getMessagesStorage().addEncryptedGroup(encryptedGroup, dialog);

        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);
        });
    }

    private void forceHidePreview(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup) {
        if (encryptedGroup.getState() != EncryptedGroupState.INITIALIZED) {
            long chatDialogId = DialogObject.makeEncryptedDialogId(encryptedChat.id);
            long groupDialogId = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());

            getMessagesController().deleteDialog(chatDialogId, 1);
            if (getMessagesController().dialogMessage.get(groupDialogId) != null) {
                getMessagesController().dialogMessage.put(groupDialogId, null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
    }

    private EncryptedGroup createEncryptedGroup(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroupByExternalId(action.externalGroupId);
        if (encryptedGroup != null) {
            log("There is already an encrypted group with external id " + action.externalGroupId);
            return null;
        }
        if (action.memberIds.size() > EncryptedGroupConstants.MAX_MEMBER_COUNT) {
            log("Too many encrypted group members");
            return null;
        }
        List<InnerEncryptedChat> encryptedChats = createInnerChats(encryptedChat, action);
        if (encryptedChats.isEmpty()) {
            return null;
        }

        EncryptedGroup.EncryptedGroupBuilder builder = new EncryptedGroup.EncryptedGroupBuilder();
        builder.setInternalId(Utilities.random.nextInt());
        builder.setExternalId(action.externalGroupId);
        builder.setName(action.name);
        builder.setInnerChats(encryptedChats);
        builder.setOwnerUserId(action.ownerUserId);
        builder.setState(EncryptedGroupState.JOINING_NOT_CONFIRMED);
        return builder.create();
    }

    private List<InnerEncryptedChat> createInnerChats(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        List<InnerEncryptedChat> innerEncryptedChats = action.memberIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id != getUserConfig().getClientUserId())
                .map(memberId -> new InnerEncryptedChat(memberId, Optional.empty()))
                .collect(Collectors.toList());

        InnerEncryptedChat ownerInnerChat = new InnerEncryptedChat(encryptedChat.user_id, Optional.of(encryptedChat.id));
        ownerInnerChat.setState(InnerEncryptedChatState.INITIALIZED);
        innerEncryptedChats.add(ownerInnerChat);

        return innerEncryptedChats;
    }

    private TLRPC.Dialog createTlrpcDialog(EncryptedGroup encryptedGroup) {
        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        dialog.id = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
        dialog.unread_count = 0;
        dialog.top_message = 0;
        dialog.last_message_date = getConnectionsManager().getCurrentTime();
        return dialog;
    }

    private void handleConfirmJoinAction(TLRPC.EncryptedChat encryptedChat, ConfirmJoinAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getState() != EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS
                && encryptedGroup.getState() != EncryptedGroupState.CREATING_ENCRYPTED_CHATS) {
            log("Invalid encrypted group state.");
            return;
        }
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
        if (innerChat.getState() != InnerEncryptedChatState.INVITATION_SENT) {
            log(encryptedGroup, "The encrypted group doesn't wait for an answer to the request.");
            return;
        }
        log(encryptedGroup, "Handle confirm join.");
        innerChat.setState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)) {
            getEncryptedGroupProtocol().requestMembersToCreateSecondaryChats(encryptedGroup);
        }
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
    }

    private void handleConfirmGroupInitialization(TLRPC.EncryptedChat encryptedChat, ConfirmGroupInitializationAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getState() != EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER) {
            log(encryptedGroup, "Doesn't wait for owner confirmation.");
            return;
        }
        log(encryptedGroup, "Owner confirmed initialization.");
        encryptedGroup.setState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        SecondaryInnerChatStarter.startSecondaryChats(accountNum, LaunchActivity.instance, encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
    }

    private void handleStartSecondaryChat(TLRPC.EncryptedChat encryptedChat, StartSecondaryInnerChatAction action) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroupByExternalId(action.externalGroupId);
        if (encryptedGroup == null) {
            log("There is no encrypted group with id " + action.externalGroupId);
            return;
        }
        if (encryptedGroup.getState() != EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION) {
            log("Invalid encrypted group state.");
            return;
        }
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
        if (innerChat == null) {
            log(encryptedGroup, "There is no inner chat for user id.");
            return;
        }
        if (innerChat.getEncryptedChatId().isPresent()) {
            log(encryptedGroup, "Inner encrypted chat is already initialized for user id.");
            return;
        }
        log(encryptedGroup, "Secondary chat creation handled.");
        innerChat.setEncryptedChatId(encryptedChat.id);
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

        EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
    }

    private void handleAllSecondaryChatsInitialized(TLRPC.EncryptedChat encryptedChat, AllSecondaryChatsInitializedAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getState() != EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION) {
            log("Invalid encrypted group state.");
            return;
        }
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
        if (innerChat.getState() != InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION) {
            log("Inner encrypted chat " + encryptedChat.id + " doesn't wait for secondary chats creation");
            return;
        }
        log(encryptedGroup, "User created all secondary chats.");
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
    }

    private void handleGroupCreationFailed(TLRPC.EncryptedChat encryptedChat, GroupCreationFailedAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getState() == EncryptedGroupState.INITIALIZED && encryptedGroup.getOwnerUserId() != encryptedChat.user_id) {
            log("Invalid encrypted group state.");
            return;
        }
        encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        getEncryptedGroupProtocol().sendGroupCreationFailedToAllMembers(encryptedGroup);
    }

    private void handleChangeGroupInfoAction(TLRPC.EncryptedChat encryptedChat, ChangeGroupInfoAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if ((action.flags & 8) != 0) {
            encryptedGroup.setName(action.name);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            });

        }
    }

    private void handleDeleteMemberAction(TLRPC.EncryptedChat encryptedChat, DeleteMemberAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getOwnerUserId() != encryptedChat.user_id) {
            log("Deleting members by non-owner " + encryptedChat.id);
            return;
        }
        if (action.userId == getUserConfig().clientUserId) {
            getMessagesController().deleteDialog(DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId()), 0, true);
        } else {
            getEncryptedGroupProtocol().removeMember(encryptedGroup, action.userId);
            if (encryptedGroup.getState() == EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION) {
                EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
            }
        }
    }

    private void handleNewAvatar(TLRPC.EncryptedChat encryptedChat, NewAvatarAction action) {
        EncryptedGroup encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
            return;
        }
        if (encryptedGroup.getOwnerUserId() != encryptedChat.user_id) {
            log("Changing avatar by non-owner " + encryptedChat.id);
            return;
        }
        final int maxWidth = 150;
        final int maxHeight = 150;
        final int colorDepth = 3;
        final int maxSize = maxWidth * maxHeight * colorDepth;
        if (action.avatarBytes.length > maxSize) {
            log("The avatar is too big: " + action.avatarBytes.length);
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(action.avatarBytes, 0, action.avatarBytes.length);
        if (bitmap == null) {
            log("The avatar is invalid");
            return;
        }
        encryptedGroup.setAvatar(bitmap);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR)
        );
    }

    private void log(String message) {
        EncryptedGroupUtils.log(accountNum, message);
    }

    private void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        EncryptedGroupUtils.log(encryptedGroup, accountNum, message);
    }

    private EncryptedGroup getEncryptedGroupByEncryptedChat(TLRPC.EncryptedChat encryptedChat) {
        return EncryptedGroupUtils.getOrLoadEncryptedGroupByEncryptedChat(encryptedChat, accountNum);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
