package org.telegram.messenger.partisan.messageinterception;

import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils;
import org.telegram.tgnet.TLRPC;

public class NotInitializedEncryptedGroupMessagesInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        long dialogId = FakePasscodeUtils.getMessageDialogId(message);
        return new InterceptionResult(new EncryptedGroupUtils(accountNum).isNotInitializedEncryptedGroup(dialogId) && message.message != null);
    }
}
