package org.telegram.messenger.partisan.secretgroups;

import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class EncryptedGroupUtils {
    public static void checkAllEncryptedChatsCreated(EncryptedGroup encryptedGroup, int accountNum) {
        if (encryptedGroup.getState() != EncryptedGroupState.WAITING_SECONDARY_CHAT_CREATION) {
            throw new RuntimeException("Invalid encrypted group state");
        }
        if (encryptedGroup.allInnerChatsMatchState(InnerEncryptedChatState.INITIALIZED)) {
            log(encryptedGroup, accountNum, "All encrypted chats initialized.");
            encryptedGroup.setState(EncryptedGroupState.INITIALIZED);
            MessagesStorage.getInstance(accountNum).updateEncryptedGroup(encryptedGroup);
            if (encryptedGroup.getOwnerUserId() != UserConfig.getInstance(accountNum).clientUserId) {
                MessagesController messagesController = MessagesController.getInstance(accountNum);
                int ownerEncryptedChatId = encryptedGroup.getOwnerEncryptedChatId();
                TLRPC.EncryptedChat ownerEncryptedChat = messagesController.getEncryptedChat(ownerEncryptedChatId);
                new EncryptedGroupProtocol(accountNum).sendAllSecondaryChatsInitialized(ownerEncryptedChat);
            }
        } else if (PartisanLog.logsAllowed()) {
            String notInitializedInnerChats = encryptedGroup.getInnerChats().stream()
                    .filter(innerChat -> innerChat.getState() != InnerEncryptedChatState.INITIALIZED)
                    .map(innerChat -> Long.toString(innerChat.getUserId()))
                    .collect(Collectors.joining(", "));
            log(encryptedGroup, accountNum, "NOT all encrypted chats initialized: " + notInitializedInnerChats.length() + ".");
        }
    }

    public static String getGroupStateDescription(EncryptedGroupState state) {
        switch (state) {
            case CREATING_ENCRYPTED_CHATS:
                return LocaleController.getString(R.string.CreatingSecretChats);
            case JOINING_NOT_CONFIRMED:
                return LocaleController.getString(R.string.JoiningNotConfirmed);
            case WAITING_CONFIRMATION_FROM_MEMBERS:
            case WAITING_CONFIRMATION_FROM_OWNER:
                return LocaleController.getString(R.string.WaitingForSecretGroupInitializationConfirmation);
            case WAITING_SECONDARY_CHAT_CREATION:
                return LocaleController.getString(R.string.WaitingForSecondaryChatsCreation);
            case INITIALIZATION_FAILED:
                return LocaleController.getString(R.string.SecretGroupInitializationFailed);
            case CANCELLED:
                return LocaleController.getString(R.string.SecretGroupCancelled);
            default:
                throw new RuntimeException("Can't return encrypted group state description for state " + state);
        }
    }

    public static void getEncryptedGroupIdByInnerEncryptedDialogIdAndExecute(long dialogId, int account, Consumer<Integer> action) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            Integer encryptedGroupId = MessagesStorage.getInstance(account)
                    .getEncryptedGroupIdByInnerEncryptedChatId(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedGroupId != null) {
                action.accept(encryptedGroupId);
            }
        }
    }

    public static boolean doForEveryInnerDialogIdIfNeeded(long encryptedGroupDialogId, int account, Consumer<Long> action) {
        MessagesController messagesController = MessagesController.getInstance(account);
        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(DialogObject.getEncryptedChatId(encryptedGroupDialogId));
        if (encryptedGroup == null) {
            return false;
        }

        for (int innerChatId : encryptedGroup.getInnerEncryptedChatIds(false)) {
            action.accept(DialogObject.makeEncryptedDialogId(innerChatId));
        }
        return true;
    }

    public static void updateEncryptedGroupUnreadCount(int encryptedGroupId, int account) {
        if (isNotInitializedEncryptedGroup(encryptedGroupId, account)) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(account);

        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId, account);
        if (encryptedGroup == null) {
            return;
        }
        TLRPC.Dialog encryptedGroupDialog = messagesController.getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        if (encryptedGroupDialog == null) {
            Utilities.globalQueue.postRunnable(() -> updateEncryptedGroupUnreadCount(encryptedGroupId, account), 100);
            return;
        }
        encryptedGroupDialog.unread_count = 0;
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            if (innerChat.getDialogId().isPresent()) {
                TLRPC.Dialog innerDialog = messagesController.getDialog(innerChat.getDialogId().get());
                if (innerDialog != null) {
                    encryptedGroupDialog.unread_count += innerDialog.unread_count;
                }
            }
        }
        MessagesStorage.getInstance(account).updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public static void updateEncryptedGroupLastMessage(int encryptedGroupId, int account) {
        if (isNotInitializedEncryptedGroup(DialogObject.makeEncryptedDialogId(encryptedGroupId), account)) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(account);

        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            return;
        }
        MessageObject lastMessage = null;
        for (InnerEncryptedChat innerChat : encryptedGroup.getInnerChats()) {
            if (!innerChat.getDialogId().isPresent()) {
                continue;
            }
            ArrayList<MessageObject> currentMessages = messagesController.dialogMessage.get(innerChat.getDialogId().get());
            if (currentMessages == null || currentMessages.isEmpty()) {
                continue;
            }
            if (lastMessage == null || currentMessages.get(0).messageOwner.date > lastMessage.messageOwner.date) {
                lastMessage = currentMessages.get(0);
            }
        }
        long groupDialogId = DialogObject.makeEncryptedDialogId(encryptedGroupId);
        if (lastMessage != null) {
            messagesController.dialogMessage.put(groupDialogId, new ArrayList<>(Collections.singletonList(lastMessage)));
        } else {
            messagesController.dialogMessage.remove(groupDialogId);
        }
    }

    public static void updateEncryptedGroupLastMessageDate(int encryptedGroupId, int account) {
        if (isNotInitializedEncryptedGroup(encryptedGroupId, account)) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(account);

        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId, account);
        if (encryptedGroup == null) {
            return;
        }
        TLRPC.Dialog encryptedGroupDialog = messagesController.getDialog(DialogObject.makeEncryptedDialogId(encryptedGroupId));
        if (encryptedGroupDialog == null) {
            Utilities.globalQueue.postRunnable(() -> updateEncryptedGroupLastMessageDate(encryptedGroupId, account), 100);
            return;
        }
        encryptedGroupDialog.last_message_date = encryptedGroup.getInnerChats().stream()
                .map(InnerEncryptedChat::getDialogId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(messagesController::getDialog)
                .filter(Objects::nonNull)
                .mapToInt(dialog -> dialog.last_message_date)
                .max()
                .orElse(0);
        MessagesStorage.getInstance(account).updateEncryptedGroupDialog(encryptedGroupDialog);
    }

    public static void showSecretGroupJoinDialog(EncryptedGroup encryptedGroup, BaseFragment fragment, int accountNum, Runnable onJoined) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        MessagesStorage messagesStorage = MessagesStorage.getInstance(accountNum);

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        TLRPC.User ownerUser = messagesController.getUser(encryptedGroup.getOwnerUserId());
        String message = LocaleController.formatString(R.string.SecretGroupJoiningConfirmation,
                UserObject.getUserName(ownerUser),
                LocaleController.getString(R.string.DeclineJoiningToSecretGroup));
        builder.setMessage(AndroidUtilities.replaceTags(message));
        builder.setPositiveButton(LocaleController.getString(R.string.JoinSecretGroup), (dialog, which) -> {
            if (encryptedGroup.getState() != EncryptedGroupState.JOINING_NOT_CONFIRMED) {
                throw new RuntimeException("Invalid encrypted group state");
            }
            boolean allMembersAreKnown = encryptedGroup.getInnerUserIds()
                    .stream()
                    .allMatch(user_id -> messagesController.getUser(user_id) != null);
            if (allMembersAreKnown) {
                encryptedGroup.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_OWNER);
                messagesStorage.updateEncryptedGroup(encryptedGroup);
                TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedGroup.getOwnerEncryptedChatId());
                log(encryptedGroup, accountNum, "Send join confirmation.");
                new EncryptedGroupProtocol(accountNum).sendJoinConfirmation(encryptedChat);
            } else {
                encryptedGroup.setState(EncryptedGroupState.INITIALIZATION_FAILED);
                messagesStorage.updateEncryptedGroup(encryptedGroup);
                TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedGroup.getOwnerEncryptedChatId());
                log(encryptedGroup, accountNum, "Not all users are known.");
                new EncryptedGroupProtocol(accountNum).sendGroupInitializationFailed(encryptedChat);
            }
            if (onJoined != null) {
                onJoined.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.DeclineJoiningToSecretGroup), (dialog, which) -> {
            long dialogId = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
            messagesController.deleteDialog(dialogId, 0, false);
        });
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    public static void showNotImplementedDialog(BaseFragment fragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(LocaleController.getString(R.string.ThisFeatureIsNotImplemented));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
    }

    static void log(int account, String message) {
        log((Long)null, account, message);
    }

    static void log(@Nullable EncryptedGroup encryptedGroup, int account, String message) {
        Long externalId = encryptedGroup != null ? encryptedGroup.getExternalId() : null;
        log(externalId, account, message);
    }

    static void log(@Nullable Long encryptedGroupExternalId, int account, String message) {
        if (encryptedGroupExternalId != null) {
            PartisanLog.d("Account: " + account + ". Encrypted group: " + encryptedGroupExternalId + ". " + message);
        } else {
            PartisanLog.d("Account: " + account + ". Encrypted group: unknown. " + message);
        }
    }

    public static EncryptedGroup getEncryptedGroupByEncryptedChat(TLRPC.EncryptedChat encryptedChat, int accountNum) {
        if (encryptedChat == null) {
            return null;
        }
        return getEncryptedGroupByEncryptedChatId(encryptedChat.id, accountNum);
    }

    public static EncryptedGroup getEncryptedGroupByEncryptedChatId(int encryptedChatId, int accountNum) {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(accountNum);
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        Integer groupId = messagesStorage.getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId);
        if (groupId == null) {
            return null;
        }
        return messagesController.getEncryptedGroup(groupId);
    }

    public static boolean isNotInitializedEncryptedGroup(long dialogId, int accountNum) {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        MessagesStorage messagesStorage = MessagesStorage.getInstance(accountNum);

        int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
        Integer encryptedGroupId = messagesStorage.getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId);
        if (encryptedGroupId == null) {
            return false;
        }
        EncryptedGroup encryptedGroup = getOrLoadEncryptedGroup(encryptedGroupId, accountNum);
        return encryptedGroup == null || encryptedGroup.getState() != EncryptedGroupState.INITIALIZED;
    }

    private static EncryptedGroup getOrLoadEncryptedGroup(int encryptedGroupId, int accountNum) {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(accountNum);
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(encryptedGroupId);
        if (encryptedGroup == null) {
            try {
                encryptedGroup = messagesStorage.loadEncryptedGroup(encryptedGroupId);
            } catch (Exception ignore) {
            }
        }
        return encryptedGroup;
    }

    public static boolean isInnerEncryptedGroupChat(long dialogId, int account) {
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        return isInnerEncryptedGroupChat(DialogObject.getEncryptedChatId(dialogId), account);
    }

    public static boolean isInnerEncryptedGroupChat(TLRPC.EncryptedChat encryptedChat, int account) {
        if (encryptedChat == null) {
            return false;
        }
        return isInnerEncryptedGroupChat(encryptedChat.id, account);
    }

    public static boolean isInnerEncryptedGroupChat(int encryptedChatId, int account) {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(account);
        return messagesStorage.getEncryptedGroupIdByInnerEncryptedChatId(encryptedChatId) != null;
    }

    public static boolean encryptedGroupsEnabled() {
        return !FakePasscodeUtils.isFakePasscodeActivated()
                && SharedConfig.encryptedGroupsEnabled;
    }

    public static boolean putEncIdOrEncGroupIdInBundle(Bundle bundle, long dialogId, int account) {
        EncryptedGroup encryptedGroup = MessagesController.getInstance(account)
                .getEncryptedGroup(DialogObject.getEncryptedChatId(dialogId));
        if (encryptedGroup != null) {
            if (encryptedGroup.getState() == EncryptedGroupState.JOINING_NOT_CONFIRMED) {
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
}
