package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.Utils;

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
        getMessagesController().getAllEncryptedGroups()
                .stream()
                .filter(encryptedGroup -> encryptedGroup.isNotInState(EncryptedGroupState.INITIALIZED))
                .map(encryptedGroup -> encryptedGroup.getInnerChatByUserId(encryptedGroup.getOwnerUserId()))
                .filter(innerChat -> innerChat != null && innerChat.getEncryptedChatId().isPresent())
                .map(innerChat -> DialogObject.makeEncryptedDialogId(innerChat.getEncryptedChatId().get()))
                .forEach(
                        innerDialogId -> getMessagesStorage().deleteEncryptedGroupPreview(innerDialogId)
                );
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
