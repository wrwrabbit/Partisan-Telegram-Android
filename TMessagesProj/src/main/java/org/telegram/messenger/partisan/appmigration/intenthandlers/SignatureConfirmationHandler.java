package org.telegram.messenger.partisan.appmigration.intenthandlers;

import android.app.Activity;
import android.content.Intent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.appmigration.AppMigrator;

public class SignatureConfirmationHandler extends AbstractIntentHandler {
    private static boolean signatureConfirmationSent = false;

    @Override
    public boolean needHandleIntent(Intent intent, Activity activity) {
        return intent.getBooleanExtra("signatureConfirmationRequired", false)
                && allowResponseSignatureConfirmation()
                && !signatureConfirmationSent;
    }

    private static boolean allowResponseSignatureConfirmation() {
        return !AppMigrator.appAlreadyHasAccounts();
    }

    @Override
    public void handleIntent(Intent intent, Activity activity) {
        AndroidUtilities.runOnUIThread(() -> {
            String packageName = intent.getStringExtra("packageName");
            String activityName = intent.getStringExtra("activityName");
            if (packageName != null && activityName != null) {
                PartisanLog.d("MigrationZipReceiver: intent packageName " + packageName + ", intent activityName " + activityName);
                signatureConfirmationSent = true;
                Intent requestZipIntent = new Intent(Intent.ACTION_MAIN);
                requestZipIntent.setClassName(packageName, activityName);
                requestZipIntent.putExtra("fromOtherPtg", true);
                requestZipIntent.putExtra("requestZip", true);
                activity.startActivityForResult(requestZipIntent, AppMigrator.CONFIRM_SIGNATURE_CODE);
            } else {
                PartisanLog.d("MigrationZipReceiver: intent packageName or activityName is null");
            }
        });
    }
}
