package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.tgnet.TLRPC;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class MembersAdder implements AccountControllersProvider {
    private final int accountNum;
    private final EncryptedGroup encryptedGroup;
    private final List<TLRPC.User> users = new LinkedList<>();

    public MembersAdder(int accountNum, EncryptedGroup encryptedGroup, List<TLRPC.User> users) {
        this.accountNum = accountNum;
        this.users.addAll(users);
        this.encryptedGroup = encryptedGroup;
    }

    public static void addNewMembers(int accountNum, List<TLRPC.User> users, EncryptedGroup encryptedGroup) {
        if (users == null || users.isEmpty()) {
            return;
        }
        new MembersAdder(accountNum, encryptedGroup, users).start();
    }

    public void start() {
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
