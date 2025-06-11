package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.CREATING_ENCRYPTED_CHATS;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.INITIALIZED;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.JOINING_NOT_CONFIRMED;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER;
import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.secretgroups.action.AbstractCreateGroupAction;
import org.telegram.messenger.partisan.secretgroups.action.AddMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.AllSecondaryChatsInitializedAction;
import org.telegram.messenger.partisan.secretgroups.action.ChangeGroupInfoAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmGroupInitializationAction;
import org.telegram.messenger.partisan.secretgroups.action.ConfirmJoinAction;
import org.telegram.messenger.partisan.secretgroups.action.DeleteAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.DeleteMemberAction;
import org.telegram.messenger.partisan.secretgroups.action.ExternalGroupIdProvider;
import org.telegram.messenger.partisan.secretgroups.action.GroupCreationFailedAction;
import org.telegram.messenger.partisan.secretgroups.action.NewAvatarAction;
import org.telegram.messenger.partisan.secretgroups.action.StartSecondaryInnerChatAction;
import org.telegram.tgnet.TLRPC;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EncryptedGroupServiceMessagesHandler implements AccountControllersProvider {
    private enum HandlerCondition {
        GROUP_EXISTS,
        GROUP_NOT_EXISTS,
        INNER_CHAT_EXISTS,
        ACTION_FROM_OWNER,
        CURRENT_USER_IS_NOT_OWNER,
        FAKE_PASSCODE_NOT_ACTIVATED,
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Handler {
        HandlerCondition[] conditions() default {};
        EncryptedGroupState[] groupStates() default {};
        InnerEncryptedChatState[] innerChatStates() default{};
    }

    private static class AssertionException extends RuntimeException {
    }

    private final TLRPC.EncryptedChat encryptedChat;
    private final EncryptedGroupsServiceMessage serviceMessage;
    private final int date;
    private final int accountNum;
    private EncryptedGroup encryptedGroup;
    private InnerEncryptedChat innerChat;

    public EncryptedGroupServiceMessagesHandler(TLRPC.EncryptedChat encryptedChat,
                                                EncryptedGroupsServiceMessage serviceMessage,
                                                int date,
                                                int accountNum) {
        this.encryptedChat = encryptedChat;
        this.serviceMessage = serviceMessage;
        this.date = date;
        this.accountNum = accountNum;
    }

    public TLRPC.Message handleServiceMessage() {
        log("Handle service message " + serviceMessage.encryptedGroupAction.getClass());

        Method[] methods = EncryptedGroupServiceMessagesHandler.class.getDeclaredMethods();
        for (Method method : methods) {
            if (validateMethod(method)) {
                try {
                    return invokeMethod(method);
                } catch (AssertionException ignore) {
                    return null;
                }
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

        encryptedGroup = getEncryptedGroup();
        if (encryptedGroup != null) {
            innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
        }
        return validateHandlerConditions(annotation.conditions())
                && validateGroupState(annotation.groupStates())
                && validateInnerChatState(annotation.innerChatStates());
    }

    private EncryptedGroup getEncryptedGroup() {
        Long externalId = extractExternalGroupIdFromAction();
        if (externalId != null) {
            return getEncryptedGroupUtils().getOrLoadEncryptedGroupByExternalId(externalId);
        } else {
            return getEncryptedGroupUtils().getOrLoadEncryptedGroupByEncryptedChat(encryptedChat);
        }
    }

    private Long extractExternalGroupIdFromAction() {
        if (serviceMessage.encryptedGroupAction instanceof ExternalGroupIdProvider) {
            return ((ExternalGroupIdProvider)serviceMessage.encryptedGroupAction).getExternalGroupId();
        } else {
            return null;
        }
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
                case GROUP_NOT_EXISTS:
                    if (encryptedGroup != null) {
                        log("There is already an encrypted group with the external id");
                        return false;
                    }
                    break;
                case INNER_CHAT_EXISTS:
                    if (innerChat == null) {
                        log("There is no inner chat with the user");
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
                case CURRENT_USER_IS_NOT_OWNER:
                    if (encryptedGroup == null) {
                        throw new RuntimeException("Wrong annotation usage");
                    }
                    if (encryptedGroup.getOwnerUserId() == getUserConfig().clientUserId) {
                        log("Action sent to owner");
                        return false;
                    }
                    break;
                case FAKE_PASSCODE_NOT_ACTIVATED:
                    if (FakePasscodeUtils.isFakePasscodeActivated()) {
                        log("Fake passcode activated");
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
            if (encryptedGroup.isNotInState(states)) {
                log("Invalid encrypted group state.");
                return false;
            }
        }
        return true;
    }

    private boolean validateInnerChatState(InnerEncryptedChatState[] states) {
        if (states.length > 0) {
            if (innerChat == null) {
                throw new RuntimeException("Wrong annotation usage");
            }
            if (innerChat.isNotInState(states)) {
                log("Invalid inner chat state.");
                return false;
            }
        }
        return true;
    }

    private TLRPC.Message invokeMethod(Method method) {
        try {
            log("Invoke service message handler method " + method.getName());
            return (TLRPC.Message)method.invoke(this, serviceMessage.encryptedGroupAction);
        } catch (IllegalAccessException | InvocationTargetException e) {
            if (e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() instanceof AssertionException) {
                return null;
            }
            PartisanLog.e("invokeMethod exception " + method.getName(), e);
            throw new RuntimeException(e);
        }
    }

    @Handler(conditions = {HandlerCondition.GROUP_NOT_EXISTS, HandlerCondition.FAKE_PASSCODE_NOT_ACTIVATED})
    private TLRPC.Message handleGroupCreation(AbstractCreateGroupAction action) {
        encryptedGroup = createEncryptedGroup(encryptedChat, action);
        log("Created.");

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
        return createMessageForStoring();
    }

    private EncryptedGroup createEncryptedGroup(TLRPC.EncryptedChat encryptedChat, AbstractCreateGroupAction action) {
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
        builder.setState(action.getInitialEncryptedGroupState());
        return builder.create();
    }

    private List<InnerEncryptedChat> createInnerChats(TLRPC.EncryptedChat encryptedChat, AbstractCreateGroupAction action) {
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

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.INNER_CHAT_EXISTS},
            groupStates = {WAITING_CONFIRMATION_FROM_MEMBERS, CREATING_ENCRYPTED_CHATS},
            innerChatStates = {InnerEncryptedChatState.INVITATION_SENT})
    private TLRPC.Message handleConfirmJoinAction(ConfirmJoinAction action) {
        log("Handle confirm join.");
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

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.INNER_CHAT_EXISTS},
            groupStates = INITIALIZED,
            innerChatStates = InnerEncryptedChatState.NEW_MEMBER_INVITATION_SENT)
    private TLRPC.Message handleConfirmNewMemberJoinAction(ConfirmJoinAction action) {
        innerChat.setState(InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        if (encryptedGroup.hasAvatar()) {
            getEncryptedGroupProtocol().sendNewAvatar(encryptedGroup, encryptedChat);
        }
        syncNewInnerChatTtl();
        syncNewInnerChatNotificationSettings();
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        return null;
    }

    private void syncNewInnerChatTtl() {
        encryptedGroup.getInnerEncryptedChatIds(false).stream()
                .map(id -> getMessagesController().getEncryptedChat(id))
                .filter(Objects::nonNull)
                .filter(otherEncryptedChat -> otherEncryptedChat.ttl != encryptedChat.ttl)
                .findFirst()
                .ifPresent(otherEncryptedChat ->
                        AndroidUtilities.runOnUIThread(() -> {
                            encryptedChat.ttl = otherEncryptedChat.ttl;
                            getSecretChatHelper().sendTTLMessage(encryptedChat, null);
                            getMessagesStorage().updateEncryptedChatTTL(encryptedChat);
                        })
                );
    }

    private void syncNewInnerChatNotificationSettings() {
        Long otherDialogId = encryptedGroup.getInnerEncryptedChatIds(false).stream()
                .filter(id -> id != encryptedChat.id)
                .map(DialogObject::makeEncryptedDialogId)
                .findFirst()
                .orElse(null);
        if (otherDialogId == null) {
            return;
        }
        syncNotificationSetting("notify2_", otherDialogId);
        syncNotificationSetting("notifyuntil_", otherDialogId);
        syncNotificationSetting("sound_enabled_", otherDialogId);
    }

    private <T> void syncNotificationSetting(String settingsTypeKey, long sourceDialogId) {
        String sourceKey = makeNotificationSettingsKey(settingsTypeKey, sourceDialogId);
        String destinationKey = makeNotificationSettingsKey(settingsTypeKey, DialogObject.makeEncryptedDialogId(encryptedChat.id));
        SharedPreferences preferences = MessagesController.getNotificationsSettings(accountNum);
        if (!preferences.contains(sourceKey)) {
            return;
        }
        Map<String, ?> allValues = preferences.getAll();
        Object value = allValues.get(sourceKey);
        SharedPreferences.Editor editor = preferences.edit();
        if (value instanceof Integer) {
            editor.putInt(destinationKey, (int)value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(destinationKey, (boolean)value);
        }
        editor.apply();
    }

    private static String makeNotificationSettingsKey(String settingsTypeKey, long dialogId) {
        return settingsTypeKey + NotificationsController.getSharedPrefKey(dialogId, 0);
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER}, groupStates = WAITING_CONFIRMATION_FROM_OWNER)
    private TLRPC.Message handleConfirmGroupInitialization(ConfirmGroupInitializationAction action) {
        encryptedGroup.setState(WAITING_SECONDARY_CHAT_CREATION);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        getEncryptedGroupUtils().finalizeEncryptedGroupIfAllChatsCreated(encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.CURRENT_USER_IS_NOT_OWNER, HandlerCondition.INNER_CHAT_EXISTS})
    private TLRPC.Message handleStartSecondaryChat(StartSecondaryInnerChatAction action) {
        innerAssert(!innerChat.getEncryptedChatId().isPresent(), "Inner encrypted chat is already initialized for user id.");
        innerAssert(areSecondaryChatStatesValid(encryptedGroup.getState(), innerChat.getState()), "Invalid encrypted group state or inner chat state.");

        log("Secondary chat creation handled.");
        innerChat.setEncryptedChatId(encryptedChat.id);
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

        if (encryptedGroup.isNotInState(INITIALIZED)) {
            getEncryptedGroupUtils().finalizeEncryptedGroupIfAllChatsCreated(encryptedGroup);
        }
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        syncNewInnerChatTtl();
        syncNewInnerChatNotificationSettings();
        return null;
    }

    private static boolean areSecondaryChatStatesValid(EncryptedGroupState groupState, InnerEncryptedChatState innerChatState) {
        if (innerChatState == InnerEncryptedChatState.CREATING_ENCRYPTED_CHAT) {
            return groupState == WAITING_SECONDARY_CHAT_CREATION;
        } else if (innerChatState == InnerEncryptedChatState.NEW_MEMBER_WAITING_SECONDARY_CHATS_CREATION) {
            return groupState == INITIALIZED;
        } else {
            return false;
        }
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.INNER_CHAT_EXISTS},
            groupStates = WAITING_SECONDARY_CHAT_CREATION,
            innerChatStates = InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)
    private TLRPC.Message handleAllSecondaryChatsInitialized(AllSecondaryChatsInitializedAction action) {
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
        getEncryptedGroupUtils().finalizeEncryptedGroupIfAllChatsCreated(encryptedGroup);
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupUpdated, encryptedGroup);
        });
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.INNER_CHAT_EXISTS},
            groupStates = INITIALIZED,
            innerChatStates = InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION)
    private TLRPC.Message handleNewMemberAllSecondaryChatsInitialized(AllSecondaryChatsInitializedAction action) {
        innerChat.setState(InnerEncryptedChatState.INITIALIZED);
        getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);
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
        if ((action.flags & ChangeGroupInfoAction.FLAG_NAME) != 0) {
            encryptedGroup.setName(action.name);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
            });

            TLRPC.TL_messageService newMessage = createMessageForStoring();
            newMessage.action.title = action.name;
            return newMessage;
        }
        return null;
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleDeleteMemberAction(DeleteMemberAction action) {
        if (action.userId == getUserConfig().clientUserId) {
            getMessagesController().deleteDialog(DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId()), 0, true);
        } else {
            getEncryptedGroupProtocol().removeMember(encryptedGroup, action.userId);
            if (encryptedGroup.isInState(WAITING_SECONDARY_CHAT_CREATION)) {
                getEncryptedGroupUtils().finalizeEncryptedGroupIfAllChatsCreated(encryptedGroup);
            }
        }
        return createMessageForStoring();
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER}, groupStates = INITIALIZED)
    private TLRPC.Message handleAddMemberAction(AddMemberAction action) {
        innerAssert(action.userId != getUserConfig().clientUserId, "Can't add myself");
        innerAssert(encryptedGroup.getInnerChatByUserId(action.userId) == null, "The member already exists");
        InnerEncryptedChat innerChat = new InnerEncryptedChat(action.userId, Optional.empty());
        innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_WAITING_SECONDARY_CHATS_CREATION);
        encryptedGroup.addInnerChat(innerChat);
        getMessagesStorage().addEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat.getUserId(), innerChat.getState());
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedGroupMembersAdded, encryptedGroup.getInternalId())
        );
        return createMessageForStoring();
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleNewAvatar(NewAvatarAction action) {
        innerAssert(encryptedGroup.isNotInState(JOINING_NOT_CONFIRMED), "Invalid encrypted group state.");
        final int maxWidth = 150;
        final int maxHeight = 150;
        final int colorDepth = 3;
        final int maxSize = maxWidth * maxHeight * colorDepth;
        innerAssert(action.avatarBytes.length <= maxSize, "The avatar is too big: " + action.avatarBytes.length);

        Bitmap bitmap = BitmapFactory.decodeByteArray(action.avatarBytes, 0, action.avatarBytes.length);
        innerAssert(bitmap != null, "The avatar is invalid");
        encryptedGroup.setAvatar(bitmap);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR)
        );
        return createMessageForStoring();
    }

    @Handler(conditions = {HandlerCondition.GROUP_EXISTS, HandlerCondition.ACTION_FROM_OWNER})
    private TLRPC.Message handleDeleteAvatar(DeleteAvatarAction action) {
        innerAssert(encryptedGroup.hasAvatar(), "The group doesn't have avatar");
        encryptedGroup.setAvatar(null);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR)
        );
        return createMessageForStoring();
    }

    private TLRPC.TL_messageService createMessageForStoring() {
        TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
        newMessage.action = new TLRPC.TL_messageEncryptedAction();
        newMessage.action.encryptedAction = serviceMessage.encryptedGroupAction;

        newMessage.local_id = newMessage.id = getUserConfig().getNewMessageId();
        getUserConfig().saveConfig(false);
        newMessage.unread = true;
        newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMessage.date = date;
        newMessage.from_id = new TLRPC.TL_peerUser();

        long from_id = encryptedChat.admin_id;
        if (from_id == getUserConfig().getClientUserId()) {
            from_id = encryptedChat.participant_id;
        }

        newMessage.from_id.user_id = from_id;
        newMessage.peer_id = new TLRPC.TL_peerUser();
        newMessage.peer_id.user_id = getUserConfig().getClientUserId();
        newMessage.dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        return newMessage;
    }

    private void innerAssert(boolean condition, String errorMessage) {
        if (!condition) {
            log(errorMessage);
            throw new AssertionException();
        }
    }

    private void log(String message) {
        getEncryptedGroupUtils().log(encryptedGroup, message);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
