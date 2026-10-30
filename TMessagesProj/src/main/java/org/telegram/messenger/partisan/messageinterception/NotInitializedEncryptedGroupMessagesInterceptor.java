package org.telegram.messenger.partisan.messageinterception;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils;
import org.telegram.tgnet.TLRPC;

public class NotInitializedEncryptedGroupMessagesInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        long dialogId = Utils.getMessageDialogId(message);
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return new InterceptionResult(false);
        }
        EncryptedGroupUtils encryptedGroupUtils = new EncryptedGroupUtils(accountNum);
        encryptedGroupUtils.cacheEncryptedGroupBlockingIfNeeded(dialogId);
        EncryptedGroup encryptedGroup = MessagesController.getInstance(accountNum)
                .getEncryptedGroupByInnerEncryptedChatId(DialogObject.getEncryptedChatId(dialogId));
        boolean isNotInitialized = encryptedGroup != null && encryptedGroupUtils.isNotInitializedEncryptedGroup(encryptedGroup.getInternalId());
        return new InterceptionResult(isNotInitialized && message.message != null);
    }
}
