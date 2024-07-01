package org.telegram.messenger.partisan.messageinterception;

import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
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
        return passcode.isPreventMessageSaving(accountNum, getMessageDialogId(message));
    }

    private long getMessageDialogId(TLRPC.Message message) {
        if (message.dialog_id != 0) {
            return message.dialog_id;
        } else if (message.from_id != null) {
            if (message.from_id instanceof TLRPC.TL_peerUser) {
                return message.from_id.user_id;
            } else if (message.from_id instanceof TLRPC.TL_peerChannel) {
                return -message.from_id.channel_id;
            } else if (message.from_id instanceof TLRPC.TL_peerChat) {
                return -message.from_id.chat_id;
            }
        }
        return 0;
    }
}
