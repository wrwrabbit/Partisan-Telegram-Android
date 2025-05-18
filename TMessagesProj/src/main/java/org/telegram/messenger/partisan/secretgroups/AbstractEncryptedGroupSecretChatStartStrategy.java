package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

public abstract class AbstractEncryptedGroupSecretChatStartStrategy implements SecretChatHelper.SecretChatStartStrategy {
    @Override
    public void onError(TLRPC.TL_error error) {
        if (error.text.startsWith("FLOOD_WAIT")) {
            int floodWait = Utilities.parseInt(error.text);
            Utilities.globalQueue.postRunnable(this::retryEncryptedChatStart, (floodWait + 1) * 1000L);
        }
    }

    protected abstract void retryEncryptedChatStart();

    @Override
    public boolean allowShowingErrorDialog() {
        return false;
    }
}
