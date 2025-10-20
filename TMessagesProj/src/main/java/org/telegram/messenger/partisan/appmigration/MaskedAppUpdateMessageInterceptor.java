package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.messageinterception.InterceptionResult;
import org.telegram.messenger.partisan.messageinterception.MessageInterceptor;
import org.telegram.messenger.partisan.update.UpdateData;
import org.telegram.tgnet.TLRPC;

public class MaskedAppUpdateMessageInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        trySaveMaskedUpdateDocument(message);
        return new InterceptionResult(false);
    }

    private synchronized void trySaveMaskedUpdateDocument(TLRPC.Message message) {
        if (isMaskedUpdateDocument(message)) {
            PartisanLog.d("UpdateChecker: masked document received");
            SharedConfig.pendingPtgAppUpdate.document = message.media.document;
            SharedConfig.saveConfig();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.maskedUpdateReceived)
            );
        } else if (isUpdateRequestFailedMessage(message)) {
            SharedConfig.pendingPtgAppUpdate.botRequestTag = null;
            PartisanLog.d("UpdateChecker: update request failed");
            SharedConfig.saveConfig();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.maskedUpdateReceived)
            );
        }
    }

    private boolean isMaskedUpdateDocument(TLRPC.Message message) {
        UpdateData update = SharedConfig.pendingPtgAppUpdate;
        if (update == null || update.botRequestTag == null || update.isMaskedUpdateDocument()) {
            return false;
        }
        String targetFileName = "update-" + SharedConfig.pendingPtgAppUpdate.botRequestTag + ".apk";
        return message != null
                && message.from_id.user_id == MaskedMigratorHelper.MASKING_BOT_ID
                && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals(targetFileName);
    }

    private boolean isUpdateRequestFailedMessage(TLRPC.Message message) {
        UpdateData update = SharedConfig.pendingPtgAppUpdate;
        if (update == null || update.botRequestTag == null || update.isMaskedUpdateDocument()) {
            return false;
        }
        String prefix = "#update-request-failed-" + SharedConfig.pendingPtgAppUpdate.botRequestTag;
        return message != null
                && message.from_id.user_id == MaskedMigratorHelper.MASKING_BOT_ID
                && message.message != null
                && message.message.startsWith(prefix);
    }
}
