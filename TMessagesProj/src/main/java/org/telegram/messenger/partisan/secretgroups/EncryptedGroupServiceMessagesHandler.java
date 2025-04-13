package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.CREATING_ENCRYPTED_CHATS;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.INITIALIZED;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION;

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
import org.telegram.messenger.partisan.secretgroups.action.GroupCreationFailedAction;
import org.telegram.messenger.partisan.secretgroups.action.NewAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupServiceMessagesHandler implements AccountControllersProvider {
    private enum HandlerCondition {
        GROUP_EXISTS,
        ACTION_FROM_OWNER
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Handler {
        HandlerCondition[] conditions() default {};
        EncryptedGroupState[] groupStates() default {};
        InnerEncryptedChatState[] innerChatStates() default{};
    }

    private final TLRPC.EncryptedChat encryptedChat;
    private final int accountNum;
    private final EncryptedGroupsServiceMessage serviceMessage;
    private EncryptedGroup encryptedGroup;

    public EncryptedGroupServiceMessagesHandler(TLRPC.EncryptedChat encryptedChat,
                                                EncryptedGroupsServiceMessage serviceMessage,
                                                int accountNum) {
        this.encryptedChat = encryptedChat;
        this.serviceMessage = serviceMessage;
        this.accountNum = accountNum;
    }

    public TLRPC.Message handleServiceMessage() {
        if (!SharedConfig.encryptedGroupsEnabled) {
            return null;
        }
        log("Handle service message " + serviceMessage.encryptedGroupAction.getClass());

        Method[] methods = EncryptedGroupServiceMessagesHandler.class.getDeclaredMethods();
        for (Method method : methods) {
            if (validateMethod(method)) {
                return invokeMethod(method);
            }
        }
        return null;
    }

    private boolean validateMethod(Method method) {
        Handler annotation = method.getAnnotation(Handler.class);
        if (annotation == null) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 1 || !parameters[0].isInstance(serviceMessage.encryptedGroupAction)) {
            return false;
        }

        encryptedGroup = getEncryptedGroupByEncryptedChat(encryptedChat);
        if (!validateHandlerConditions(annotation.conditions())
                || !validateGroupState(annotation.groupStates())
                || !validateInnerChatState(annotation.innerChatStates())) {
            return false;
        }
        return true;
    }

    private boolean validateHandlerConditions(HandlerCondition[] conditions) {
        for (HandlerCondition condition : conditions) {
            switch (condition) {
                case GROUP_EXISTS:
                    if (encryptedGroup == null) {
                        log("There is no encrypted group contained encrypted chat with id " + encryptedChat.id);
                        return false;
                    }
                    break;
                case ACTION_FROM_OWNER:
                    if (encryptedGroup == null) {
                        throw new RuntimeException("Wrong annotation usage");
                    }
                    if (encryptedGroup.getOwnerUserId() != encryptedChat.user_id) {
                        log("Action sent by non-owner " + encryptedChat.id);
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    private boolean validateGroupState(EncryptedGroupState[] states) {
        if (states.length > 0) {
            if (encryptedGroup == null) {
                throw new RuntimeException("Wrong annotation usage");
            }
            List<EncryptedGroupState> stateList = Arrays.asList(states);
            if (stateList.stream().noneMatch(state -> state == encryptedGroup.getState())) {
                log("Invalid encrypted group state.");
                return false;
            }
        }
        return true;
    }

    private boolean validateInnerChatState(InnerEncryptedChatState[] states) {
        if (states.length > 0) {
            if (encryptedGroup == null) {
                throw new RuntimeException("Wrong annotation usage");
            }
            List<InnerEncryptedChatState> stateList = Arrays.asList(states);
            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
            if (stateList.stream().noneMatch(state -> state == innerChat.getState())) {
                log("Invalid inner chat state.");
                return false;
            }
        }
        return true;
    }

    private TLRPC.Message invokeMethod(Method method) {
        try {
            return (TLRPC.Message)method.invoke(this, serviceMessage.encryptedGroupAction);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Handler()
    private TLRPC.Message handleGroupCreation(TLRPC.EncryptedChat encryptedChat, CreateGroupAction action) {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return null;
        }
        EncryptedGroup encryptedGroup = createEncryptedGroup(encryptedChat, action);
        if (encryptedGroup == null) {
            return null;
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
        return null;
    }

    private void forceHidePreview(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup) {
        if (encryptedGroup.getState() != INITIALIZED) {
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

    @Handler(conditions = HandlerCondition.GROUP_EXISTS,
            groupStates = {WAITING_CONFIRMATION_FROM_MEMBERS, CREATING_ENCRYPTED_CHATS},
            innerChatStates = InnerEncryptedChatState.INVITATION_SENT)
    private TLRPC.Message handleConfirmJoinAction(TLRPC.EncryptedChat encryptedChat, EncryptedGroup encryptedGroup, ConfirmJoinAction action) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
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
        return null;
    }

    @Handler(conditions = HandlerCondition.GROUP_EXISTS, groupStates = WAITING_CONFIRMATION_FROM_OWNER)
    private TLRPC.Message handleConfirmGroupInitialization(ConfirmGroupInitializationAction action) {
        log(encryptedGroup, "Owner confirmed initialization.");
        encryptedGroup.setState(WAITING_SECONDARY_CHAT_CREATION);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        SecondaryInnerChatStarter.startSecondaryChats(accountNum, LaunchActivity.instance, encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        return null;
    }

    @Handler(conditions = HandlerCondition.GROUP_EXISTS, groupStates = WAITING_SECONDARY_CHAT_CREATION)
    private TLRPC.Message handleStartSecondaryChat(StartSecondaryInnerChatAction action) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
        if (innerChat == null) {
            log(encryptedGroup, "There is no inner chat for user id.");
            return null;
        }
        if (innerChat.getEncryptedChatId().isPresent()) {
            log(encryptedGroup, "Inner encrypted chat is already initialized for user id.");
            return null;
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
        return null;
    }

    @Handler(conditions = HandlerCondition.GROUP_EXISTS,
            groupStates = WAITING_SECONDARY_CHAT_CREATION,
            innerChatStates = InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)
    private TLRPC.Message handleAllSecondaryChatsInitialized(AllSecondaryChatsInitializedAction action) {
        InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByEncryptedChatId(encryptedChat.id);
        log(encryptedGroup, "User created all secondary chats.");
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER}, groupStates = INITIALIZED)
    private TLRPC.Message handleGroupCreationFailed(GroupCreationFailedAction action) {
        encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        getEncryptedGroupProtocol().sendGroupCreationFailedToAllMembers(encryptedGroup);
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleChangeGroupInfoAction(ChangeGroupInfoAction action) {
        if ((action.flags & 8) != 0) {
            encryptedGroup.setName(action.name);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            });
        }
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleDeleteMemberAction(DeleteMemberAction action) {
        if (action.userId == getUserConfig().clientUserId) {
            getMessagesController().deleteDialog(DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId()), 0, true);
        } else {
            getEncryptedGroupProtocol().removeMember(encryptedGroup, action.userId);
            if (encryptedGroup.getState() == WAITING_SECONDARY_CHAT_CREATION) {
                EncryptedGroupUtils.checkAllEncryptedChatsCreated(encryptedGroup, accountNum);
            }
        }
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleNewAvatar(NewAvatarAction action) {
        final int maxWidth = 150;
        final int maxHeight = 150;
        final int colorDepth = 3;
        final int maxSize = maxWidth * maxHeight * colorDepth;
        if (action.avatarBytes.length > maxSize) {
            log("The avatar is too big: " + action.avatarBytes.length);
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(action.avatarBytes, 0, action.avatarBytes.length);
        if (bitmap == null) {
            log("The avatar is invalid");
            return null;
        }
        encryptedGroup.setAvatar(bitmap);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR)
        );
        return null;
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
