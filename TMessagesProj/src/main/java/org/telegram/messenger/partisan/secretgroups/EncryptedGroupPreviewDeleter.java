package org.telegram.messenger.partisan.secretgroups;

import static org.telegram.messenger.partisan.secretgroups.InnerEncryptedChatState.INVITATION_SENT;
import static org.telegram.messenger.partisan.secretgroups.InnerEncryptedChatState.NEW_MEMBER_INVITATION_SENT;
import static org.telegram.messenger.partisan.secretgroups.InnerEncryptedChatState.NEW_MEMBER_WAITING_SECONDARY_CHATS_CREATION;
import static org.telegram.messenger.partisan.secretgroups.InnerEncryptedChatState.WAITING_SECONDARY_CHATS_CREATION;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.Utils;

import java.util.stream.Stream;

public class EncryptedGroupPreviewDeleter implements AccountControllersProvider {
    private final int accountNum;

    private static final EncryptedGroupPreviewDeleter[] Instances = new EncryptedGroupPreviewDeleter[UserConfig.MAX_ACCOUNT_COUNT];

    private EncryptedGroupPreviewDeleter(int accountNum) {
        this.accountNum = accountNum;
    }

    public synchronized static EncryptedGroupPreviewDeleter getInstance(int accountNum) {
        if (Instances[accountNum] == null) {
            Instances[accountNum] = new EncryptedGroupPreviewDeleter(accountNum);
        }
        return Instances[accountNum];
    }

    public static void deletePreviewForAllAccounts() {
        Utils.foreachActivatedAccountInstance(accountInstance ->
            Utilities.stageQueue.postRunnable(() -> {
                getInstance(accountInstance.getCurrentAccount()).deletePreview();
            })
        );
    }

    private void deletePreview() {
        Stream.concat(getOwnerInnerChatsFromNotInitializedEncryptedGroups(), getNotInitializedInnerChatsFromOwnedGroups())
                .filter(innerChat -> innerChat != null && innerChat.getEncryptedChatId().isPresent())
                .map(innerChat -> DialogObject.makeEncryptedDialogId(innerChat.getEncryptedChatId().get()))
                .forEach(
                        innerDialogId -> getMessagesStorage().deleteEncryptedGroupPreview(innerDialogId)
                );
    }

    private Stream<InnerEncryptedChat> getOwnerInnerChatsFromNotInitializedEncryptedGroups() {
        return getMessagesController().getAllEncryptedGroups()
                .stream()
                .filter(encryptedGroup -> encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED))
                .map(encryptedGroup -> encryptedGroup.getInnerChatByUserId(encryptedGroup.getOwnerUserId()));
    }

    private Stream<InnerEncryptedChat> getNotInitializedInnerChatsFromOwnedGroups() {
        return getMessagesController().getAllEncryptedGroups()
                .stream()
                .filter(encryptedGroup -> encryptedGroup.getOwnerUserId() == getUserConfig().getClientUserId())
                .flatMap(encryptedGroup -> encryptedGroup.getInnerChats().stream())
                .filter(innerEncryptedChat -> innerEncryptedChat.isInState(
                        INVITATION_SENT,
                        NEW_MEMBER_INVITATION_SENT,
                        WAITING_SECONDARY_CHATS_CREATION,
                        NEW_MEMBER_WAITING_SECONDARY_CHATS_CREATION
                ));
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
