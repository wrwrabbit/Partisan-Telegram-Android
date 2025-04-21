package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils.log;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class MembersAdder implements AccountControllersProvider {
    private final int accountNum;
    private final Context context;
    private final EncryptedGroup encryptedGroup;
    private final List<TLRPC.User> users = new LinkedList<>();
    private final List<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
    private final Runnable callback;

    public MembersAdder(int accountNum, Context context, EncryptedGroup encryptedGroup, List<TLRPC.User> users, Runnable callback) {
        this.accountNum = accountNum;
        this.context = context;
        this.users.addAll(users);
        this.callback = callback;
        this.encryptedGroup = encryptedGroup;
    }

    public static void addNewMembers(int accountNum, Context context, List<TLRPC.User> users, EncryptedGroup encryptedGroup, Runnable callback) {
        if (users == null || users.isEmpty() || context == null) {
            return;
        }
        new MembersAdder(accountNum, context, encryptedGroup, users, callback).start();
    }

    public void start() {
        for (TLRPC.User user : users) {
            InnerEncryptedChat innerChat = new InnerEncryptedChat(user.id, Optional.empty());
            innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_CREATING_ENCRYPTED_CHAT);
            encryptedGroup.addInnerChat(innerChat);
            getMessagesStorage().addEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat.getUserId(), innerChat.getState());
            getEncryptedGroupProtocol().sendAddMember(encryptedGroup, user.id);
        }
        AndroidUtilities.runOnUIThread(this::checkInnerEncryptedChats);
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
        log(accountNum, "Start inner encrypted chat with new member.");
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
            innerChat.setState(InnerEncryptedChatState.NEW_MEMBER_NEED_SEND_INVITATION);
            getMessagesStorage().updateEncryptedGroupInnerChat(encryptedGroup.getInternalId(), innerChat);

            checkInnerEncryptedChats();
        } else {
            callback.run();
        }
    }

    private void onAllEncryptedChatsCreated() {
        AndroidUtilities.runOnUIThread(() -> {
            log(encryptedGroup, accountNum, "Members have been added.");
            callback.run();
        });
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
