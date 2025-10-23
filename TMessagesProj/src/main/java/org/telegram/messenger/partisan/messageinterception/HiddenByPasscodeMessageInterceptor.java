package org.telegram.messenger.partisan.messageinterception;

import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;

public class HiddenByPasscodeMessageInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        return new InterceptionResult(isMessageHiddenByPasscode(accountNum, message));
    }

    private boolean isMessageHiddenByPasscode(int accountNum, TLRPC.Message message) {
        FakePasscode passcode = FakePasscodeUtils.getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        return passcode.isPreventMessageSaving(accountNum, Utils.getMessageDialogId(message));
    }
}
