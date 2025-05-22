package org.telegram.messenger.partisan.secretgroups;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.TLRPC;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EncryptedGroupStarter implements AccountControllersProvider {
    private final int accountNum;
    private final List<TLRPC.User> users = new LinkedList<>();
    private final String name;
    private final Consumer<Optional<EncryptedGroup>> callback;

    private EncryptedGroup encryptedGroup;

    public EncryptedGroupStarter(int accountNum, List<TLRPC.User> users, String name, Consumer<Optional<EncryptedGroup>> callback) {
        this.accountNum = accountNum;
        this.users.addAll(users);
        this.name = name;
        this.callback = callback;
    }

    public static void startEncryptedGroup(int accountNum, List<TLRPC.User> users, String name, Consumer<Optional<EncryptedGroup>> callback) {
        if (users == null || users.isEmpty()) {
            return;
        }
        new EncryptedGroupStarter(accountNum, users, name, callback).start();
    }

    public void start() {
        AndroidUtilities.runOnUIThread(() -> {
            encryptedGroup = createEncryptedGroup();
            if (encryptedGroup == null) {
                callback.accept(Optional.empty());
                return;
            } else {
                callback.accept(Optional.of(encryptedGroup));
            }

            TLRPC.Dialog dialog = createTlrpcDialog(encryptedGroup);
            getMessagesController().dialogs_dict.put(dialog.id, dialog);
            getMessagesController().addDialog(dialog);
            getMessagesController().sortDialogs(null);

            getMessagesStorage().addEncryptedGroup(encryptedGroup, dialog);

            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getMessagesController().putEncryptedGroup(encryptedGroup, false);
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
