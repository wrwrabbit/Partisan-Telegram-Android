package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils.log;

import android.content.Context;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EncryptedGroupStarter implements AccountControllersProvider {
    private final int accountNum;
    private final Context context;
    private final List<TLRPC.User> users = new LinkedList<>();
    private final String name;
    private final List<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
    private final Consumer<Optional<EncryptedGroup>> callback;

    private EncryptedGroup encryptedGroup;

    public EncryptedGroupStarter(int accountNum, Context context, List<TLRPC.User> users, String name, Consumer<Optional<EncryptedGroup>> callback) {
        this.accountNum = accountNum;
        this.context = context;
        this.users.addAll(users);
        this.name = name;
        this.callback = callback;
    }

    public static void startEncryptedGroup(int accountNum, Context context, List<TLRPC.User> users, String name, Consumer<Optional<EncryptedGroup>> callback) {
        if (users == null || users.isEmpty() || context == null) {
            return;
        }
        new EncryptedGroupStarter(accountNum, context, users, name, callback).start();
    }

    public void start() {
        AndroidUtilities.runOnUIThread(() -> {
            encryptedGroup = createEncryptedGroup();
            if (encryptedGroup == null) {
                callback.accept(Optional.empty());
                return;
            }

            TLRPC.Dialog dialog = createTlrpcDialog(encryptedGroup);
            getMessagesController().dialogs_dict.put(dialog.id, dialog);
            getMessagesController().addDialog(dialog);
            getMessagesController().sortDialogs(null);

            getMessagesStorage().addEncryptedGroup(encryptedGroup, dialog);

            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);

            checkInnerEncryptedChats();
        });
    }

    private void checkInnerEncryptedChats() {
        if (users.size() != encryptedChats.size()) {
            startNextInnerEncryptedChat();
        } else {
            onAllEncryptedChatsCreated();
        }
    }

    private void startNextInnerEncryptedChat() {
        int currentUserIndex = encryptedChats.size();
        int delay = encryptedChats.isEmpty() ? 0 : 10*1000;
        TLRPC.User user = users.get(currentUserIndex);
        log(accountNum, "Start inner encrypted chat with user " + user.id + ".");
        Utilities.globalQueue.postRunnable(
                () -> getSecretChatHelper().startSecretChat(context, user, this::onInnerEncryptedChatStarted),
                delay
        );
    }

    private void onInnerEncryptedChatStarted(TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat != null) {
            encryptedChats.add(encryptedChat);

            InnerEncryptedChat innerChat = encryptedGroup.getInnerChatByUserId(encryptedChat.user_id);
            innerChat.setEncryptedChatId(encryptedChat.id);
            innerChat.setState(InnerEncryptedChatState.NEED_SEND_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

            checkInnerEncryptedChats();
        } else {
            callback.accept(Optional.empty());
        }
    }

    private void onAllEncryptedChatsCreated() {
        AndroidUtilities.runOnUIThread(() -> {
            encryptedGroup.setState(EncryptedGroupState.WAITING_CONFIRMATION_FROM_MEMBERS);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);

            log(encryptedGroup, accountNum, "Created by owner.");

            callback.accept(Optional.of(encryptedGroup));
        });
    }

    private EncryptedGroup createEncryptedGroup() {
        List<InnerEncryptedChat> encryptedChats = createEncryptedChats();
        if (encryptedChats.isEmpty()) {
            return null;
        }

        EncryptedGroup.EncryptedGroupBuilder builder = new EncryptedGroup.EncryptedGroupBuilder();
        builder.setInternalId(Utilities.random.nextInt());
        builder.setExternalId(Utilities.random.nextLong());
        builder.setName(name);
        builder.setInnerChats(encryptedChats);
        builder.setOwnerUserId(getUserConfig().clientUserId);
        builder.setState(EncryptedGroupState.CREATING_ENCRYPTED_CHATS);
        return builder.create();
    }

    private List<InnerEncryptedChat> createEncryptedChats() {
        return users.stream()
                .filter(Objects::nonNull)
                .map(user -> new InnerEncryptedChat(user.id, Optional.empty()))
                .collect(Collectors.toList());
    }

    private TLRPC.Dialog createTlrpcDialog(EncryptedGroup encryptedGroup) {
        TLRPC.Dialog dialog = new TLRPC.TL_dialog();
        dialog.id = DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
        dialog.unread_count = 0;
        dialog.top_message = 0;
        dialog.last_message_date = getConnectionsManager().getCurrentTime();
        return dialog;
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
