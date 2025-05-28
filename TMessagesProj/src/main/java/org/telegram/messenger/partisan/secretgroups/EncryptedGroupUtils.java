package org.telegram.messenger.partisan.secretgroups;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupUtils implements AccountControllersProvider {
    private final int accountNum;

    public EncryptedGroupUtils(int accountNum) {
        this.accountNum = accountNum;
    }

    public void checkAllEncryptedChatsCreated(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.isNotInState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION, EncryptedGroupState.NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION)) {
            throw new RuntimeException("Invalid encrypted group state: " + encryptedGroup.getState());
        }
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.INITIALIZED)) {
            log(encryptedGroup, "All encrypted chats initialized.");
            encryptedGroup.setState(EncryptedGroupState.INITIALIZED);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            if (encryptedGroup.getOwnerUserId() != getUserConfig().clientUserId) {
                int ownerEncryptedChatId = encryptedGroup.getOwnerEncryptedChatId();
                TLRPC.EncryptedChat ownerEncryptedChat = getMessagesController().getEncryptedChat(ownerEncryptedChatId);
                if (encryptedGroup.isInState(EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION)) {
                    getEncryptedGroupProtocol().sendAllSecondaryChatsInitialized(ownerEncryptedChat);
                }
            }
        } else if (PartisanLog.logsAllowed()) {
            String notInitializedInnerChats = encryptedGroup.getInnerChats().stream()
                    .filter(innerChat -> innerChat.isNotInState(InnerEncryptedChatState.INITIALIZED))
                    .map(innerChat -> Long.toString(innerChat.getUserId()))
                    .collect(Collectors.joining(", "));
            log(encryptedGroup, "NOT all encrypted chats initialized: " + notInitializedInnerChats.length() + ".");
        }
    }

    public static String getGroupStateDescription(EncryptedGroupState state) {
        switch (state) {
            case CREATING_ENCRYPTED_CHATS:
                return LocaleController.getString(R.string.CreatingSecretChats);
            case JOINING_NOT_CONFIRMED:
            case NEW_MEMBER_JOINING_NOT_CONFIRMED:
                return LocaleController.getString(R.string.JoiningNotConfirmed);
            case WAITING_CONFIRMATION_FROM_MEMBERS:
            case WAITING_CONFIRMATION_FROM_OWNER:
                return LocaleController.getString(R.string.WaitingForSecretGroupInitializationConfirmation);
            case WAITING_SECONDARY_CHAT_CREATION:
            case NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION:
                return LocaleController.getString(R.string.WaitingForSecondaryChatsCreation);
            case INITIALIZATION_FAILED:
                return LocaleController.getString(R.string.SecretGroupInitializationFailed);
            case CANCELLED:
                return LocaleController.getString(R.string.SecretGroupCancelled);
            default:
                throw new RuntimeException("Can't return encrypted group state description for state " + state);
        }
    }

    public void getEncryptedGroupIdByInnerEncryptedDialogIdAndExecute(long dialogId, Consumer<Integer> action) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            Integer encryptedGroupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedGroupId != null) {
                action.accept(encryptedGroupId);
            }
        }
    }

    public boolean doForEachInnerDialogIdIfNeeded(long encryptedGroupDialogId, Consumer<Long> action) {
        if (!DialogObject.isEncryptedDialog(encryptedGroupDialogId)) {
            return false;
        }
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(DialogObject.getEncryptedChatId(encryptedGroupDialogId));
        if (encryptedGroup == null) {
            return false;
        }

        for (int innerChatId : encryptedGroup.getInnerEncryptedChatIds(false)) {
            action.accept(DialogObject.makeEncryptedDialogId(innerChatId));
        }
        return true;
    }

    public void updateEncryptedGroupUnreadCount(int encryptedGroupId) {
        if (isNotInitializedEncryptedGroup(encryptedGroupId)) {
            return;
        }
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            return;
        }
        TLRPC.Dialog encryptedGroupDialog = getMessagesController().getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        if (encryptedGroupDialog == null) {
            Utilities.globalQueue.postRunnable(() -> updateEncryptedGroupUnreadCount(encryptedGroupId), 100);
            return;
        }
        encryptedGroupDialog.unread_count = 0;
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            if (innerChat.getDialogId().isPresent()) {
                TLRPC.Dialog innerDialog = getMessagesController().getDialog(innerChat.getDialogId().get());
                if (innerDialog != null) {
                    encryptedGroupDialog.unread_count += innerDialog.unread_count;
                }
            }
        }
        getMessagesStorage().updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public void updateEncryptedGroupLastMessage(int encryptedGroupId) {
        if (isNotInitializedEncryptedGroup(DialogObject.makeEncryptedDialogId(encryptedGroupId))) {
            return;
        }
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            return;
        }
        MessageObject lastMessage = null;
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            if (!innerChat.getDialogId().isPresent()) {
                continue;
            }
            ArrayList<MessageObject> currentMessages = getMessagesController().dialogMessage.get(innerChat.getDialogId().get());
            if (currentMessages == null || currentMessages.isEmpty()) {
                continue;
            }
            if (lastMessage == null || currentMessages.get(0).messageOwner.date > lastMessage.messageOwner.date) {
                lastMessage = currentMessages.get(0);
            }
        }
        long groupDialogId = DialogObject.makeEncryptedDialogId(encryptedGroupId);
        if (lastMessage != null) {
            getMessagesController().dialogMessage.put(groupDialogId, new ArrayList<>(Collections.singletonList(lastMessage)));
        } else {
            getMessagesController().dialogMessage.remove(groupDialogId);
        }
    }

    public void updateEncryptedGroupLastMessageDate(int encryptedGroupId) {
        if (isNotInitializedEncryptedGroup(encryptedGroupId)) {
            return;
        }
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            return;
        }
        TLRPC.Dialog encryptedGroupDialog = getMessagesController().getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        if (encryptedGroupDialog == null) {
            Utilities.globalQueue.postRunnable(() -> updateEncryptedGroupLastMessageDate(encryptedGroupId), 100);
            return;
        }
        encryptedGroupDialog.last_message_date = encryptedGroup.getInnerChats().stream()
                .map(InnerEncryptedChat::getDialogId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(dialogId -> getMessagesController().getDialog(dialogId))
                .filter(Objects::nonNull)
                .mapToInt(dialog -> dialog.last_message_date)
                .max()
                .orElse(0);
        getMessagesStorage().updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public void showSecretGroupJoinDialog(EncryptedGroup encryptedGroup, BaseFragment fragment, Runnable onJoined) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        TLRPC.User ownerUser = getMessagesController().getUser(encryptedGroup.getOwnerUserId());
        String message = LocaleController.formatString(R.string.SecretGroupJoiningConfirmation,
                UserObject.getUserName(ownerUser),
                LocaleController.getString(R.string.DeclineJoiningToSecretGroup));
        builder.setMessage(AndroidUtilities.replaceTags(message));
        builder.setPositiveButton(LocaleController.getString(R.string.JoinSecretGroup), (dialog, which) -> {
            tryConfirmJoining(encryptedGroup);
            if (onJoined != null) {
                onJoined.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.DeclineJoiningToSecretGroup), (dialog, which) -> {
            long dialogId = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
            getMessagesController().deleteDialog(dialogId, 0, false);
        });
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void tryConfirmJoining(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.isNotInState(EncryptedGroupState.JOINING_NOT_CONFIRMED, EncryptedGroupState.NEW_MEMBER_JOINING_NOT_CONFIRMED)) {
            throw new RuntimeException("Invalid encrypted group state");
        }
        if (canJoinToGroup(encryptedGroup)) {
            confirmJoining(encryptedGroup);
        } else {
            joiningFailed(encryptedGroup);
        }
    }

    private boolean canJoinToGroup(EncryptedGroup encryptedGroup) {
        return encryptedGroup.getInnerUserIds()
                .stream()
                .allMatch(user_id -> getMessagesController().getUser(user_id) != null);
    }

    private void confirmJoining(EncryptedGroup encryptedGroup) {
        forceHidePreview(encryptedGroup);
        for (int i = 1; i <= 20; i++) {
            AndroidUtilities.runOnUIThread(() -> forceHidePreview(encryptedGroup), 100 * i);
        }

        if (encryptedGroup.isInState(EncryptedGroupState.JOINING_NOT_CONFIRMED)) {
            encryptedGroup.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER);
        } else if (encryptedGroup.isInState(EncryptedGroupState.NEW_MEMBER_JOINING_NOT_CONFIRMED)) {
            encryptedGroup.setState(EncryptedGroupState.NEW_MEMBER_WAITING_SECONDARY_CHAT_CREATION);
        }
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);

        log(encryptedGroup, "Send join confirmation.");
        TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedGroup.getOwnerEncryptedChatId());
        getEncryptedGroupProtocol().sendJoinConfirmation(encryptedChat);
    }

    public void forceHidePreview(EncryptedGroup encryptedGroup) {
        if (encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED)) {
            Integer ownerEncryptedChatId = encryptedGroup.getInnerChatByUserId(encryptedGroup.getOwnerUserId()).getEncryptedChatId().orElse(null);
            long chatDialogId = DialogObject.makeEncryptedDialogId(ownerEncryptedChatId);
            long groupDialogId = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
            getMessagesController().deleteDialog(chatDialogId, 1);
            if (getMessagesController().dialogMessage.get(groupDialogId) != null) {
                getMessagesController().dialogMessage.put(groupDialogId, null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
    }

    private void joiningFailed(EncryptedGroup encryptedGroup) {
        encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedGroup.getOwnerEncryptedChatId());
        log(encryptedGroup, "Not all users are known.");
        getEncryptedGroupProtocol().sendGroupInitializationFailed(encryptedChat);
    }

    public static void showNotImplementedDialog(BaseFragment fragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(LocaleController.getString(R.string.ThisFeatureIsNotImplemented));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
    }

    void log(String message) {
        log((Long)null, message);
    }

    void log(@Nullable EncryptedGroup encryptedGroup, String message) {
        Long externalId = encryptedGroup != null ? encryptedGroup.getExternalId() : null;
        log(externalId, message);
    }

    void log(@Nullable Long encryptedGroupExternalId, String message) {
        if (encryptedGroupExternalId != null) {
            PartisanLog.d("Account: " + accountNum + ". Encrypted group: " + encryptedGroupExternalId + ". " + message);
        } else {
            PartisanLog.d("Account: " + accountNum + ". Encrypted group: unknown. " + message);
        }
    }

    public EncryptedGroup getOrLoadEncryptedGroupByEncryptedChat(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat == null) {
            return null;
        }
        return getOrLoadEncryptedGroupByEncryptedChatId(encryptedChat.id);
    }

    public EncryptedGroup getOrLoadEncryptedGroupByEncryptedChatId(int encryptedChatId) {
        Integer groupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId);
        if (groupId == null) {
            return null;
        }
        return getOrLoadEncryptedGroup(groupId);
    }

    public boolean isNotInitializedEncryptedGroup(long dialogId) {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
        Integer encryptedGroupId = getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId);
        if (encryptedGroupId == null) {
            return false;
        }
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId);
        return encryptedGroup == null || encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED);
    }

    public EncryptedGroup getOrLoadEncryptedGroup(int encryptedGroupId) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            try {
                encryptedGroup = getMessagesStorage().loadEncryptedGroup(encryptedGroupId);
            } catch (Exception ignore) {
            }
        }
        return encryptedGroup;
    }

    public EncryptedGroup getOrLoadEncryptedGroupByExternalId(long externalId) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroupByExternalId(externalId);
        if (encryptedGroup == null) {
            try {
                encryptedGroup = getMessagesStorage().loadEncryptedGroupByExternalId(externalId);
            } catch (Exception ignore) {
            }
        }
        return encryptedGroup;
    }

    public boolean isInnerEncryptedGroupChat(long dialogId) {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        return isInnerEncryptedGroupChat(DialogObject.getEncryptedChatId(dialogId));
    }

    public boolean isInnerEncryptedGroupChat(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat == null) {
            return false;
        }
        return isInnerEncryptedGroupChat(encryptedChat.id);
    }

    public boolean isInnerEncryptedGroupChat(int encryptedChatId) {
        return getMessagesStorage().getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId) != null;
    }

    public boolean putEncIdOrEncGroupIdInBundle(Bundle bundle, long dialogId) {
        EncryptedGroup encryptedGroup = getMessagesController().getEncryptedGroup(DialogObject.getEncryptedChatId(dialogId));
        if (encryptedGroup != null) {
            if (encryptedGroup.isInState(EncryptedGroupState.JOINING_NOT_CONFIRMED, EncryptedGroupState.NEW_MEMBER_JOINING_NOT_CONFIRMED)) {
                return false;
            } else {
                bundle.putInt("enc_group_id", encryptedGroup.getInternalId());
                return true;
            }
        } else {
            bundle.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
            return true;
        }
    }

    public List<Long> getEncryptedGroupInnerDialogIds(long dialogId) {
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(DialogObject.getEncryptedChatId(dialogId));
        return encryptedGroup.getInnerChats().stream()
                .map(innerChat -> innerChat.getDialogId().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static void applyAvatar(Object avatarImageView, AvatarDrawable avatarDrawable, EncryptedGroup encryptedGroup) {
        applyAvatar(avatarImageView, avatarDrawable, encryptedGroup, null);
    }

    public static void applyAvatar(Object avatarImageView, AvatarDrawable avatarDrawable, EncryptedGroup encryptedGroup, Object parentObject) {
        Drawable drawable;
        if (encryptedGroup != null && encryptedGroup.hasAvatar()) {
            drawable = new BitmapDrawable(encryptedGroup.getAvatar());
        } else {
            MessagesController.PeerColor peerColor = MessagesController.PeerColor.fromString("#{13770548}");
            avatarDrawable.setInfo(
                    encryptedGroup != null ? encryptedGroup.getExternalId() : 0,
                    encryptedGroup != null ? encryptedGroup.getName() : "",
                    null,
                    null,
                    null,
                    peerColor);
            drawable = avatarDrawable;
        }

        if (avatarImageView instanceof BackupImageView) {
            ((BackupImageView)avatarImageView).setImage(null, null, drawable);
        } else if (avatarImageView instanceof ImageReceiver) {
            ((ImageReceiver)avatarImageView).setImage(null, null, drawable, null, parentObject, 0);
        }
    }

    public static NativeByteBuffer serializeAvatarToByteBuffer(EncryptedGroup encryptedGroup) throws Exception {
        byte[] avatarBytes = serializeAvatar(encryptedGroup);
        NativeByteBuffer calculateBuffer = new NativeByteBuffer(true);
        calculateBuffer.writeByteArray(avatarBytes);
        NativeByteBuffer avatarBuffer = new NativeByteBuffer(calculateBuffer.length());
        avatarBuffer.writeByteArray(avatarBytes);
        return avatarBuffer;
    }

    public static byte[] serializeAvatar(EncryptedGroup encryptedGroup) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        encryptedGroup.getAvatar().compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream);
        return stream.toByteArray();
    }

    public static Bitmap deserializeAvatarFromByteBuffer(NativeByteBuffer buffer) {
        byte[] bytes = buffer.readByteArray(false);
        try {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignore) {
            return null;
        }
    }

    public void syncTtlIfNeeded(TLRPC.EncryptedChat encryptedChat) {
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroupByEncryptedChat(encryptedChat);
        if (encryptedGroup == null) {
            return;
        }
        encryptedGroup.getInnerEncryptedChatIds(false).stream()
                .map(encryptedChatId -> getMessagesController().getEncryptedChat(encryptedChatId))
                .filter(Objects::nonNull)
                .filter(otherEncryptedChat -> otherEncryptedChat.ttl != encryptedChat.ttl)
                .forEach(otherEncryptedChat ->
                    AndroidUtilities.runOnUIThread(() -> {
                        otherEncryptedChat.ttl = encryptedChat.ttl;
                        getSecretChatHelper().sendTTLMessage(otherEncryptedChat, null);
                        getMessagesStorage().updateEncryptedChatTTL(otherEncryptedChat);
                    })
                );
    }

    public void addNewMembers(EncryptedGroup encryptedGroup, List<TLRPC.User> users) {
        for (TLRPC.User user : users) {
            InnerEncryptedChat innerChat = new InnerEncryptedChat(user.id, Optional.empty());
            innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_CREATING_ENCRYPTED_CHAT);
            encryptedGroup.addInnerChat(innerChat);
            getMessagesStorage().addEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat.getUserId(), innerChat.getState());
            getEncryptedGroupProtocol().sendAddMember(encryptedGroup, user.id);
        }
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
