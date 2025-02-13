package org.telegram.messenger.partisan.appmigration.intenthandlers;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.partisan.appmigration.AppMigrator;
import org.telegram.messenger.partisan.appmigration.MigrationZipReceiver;
import org.telegram.messenger.partisan.appmigration.PackageUtils;

import java.nio.charset.StandardCharsets;

public class ZipHandler extends AbstractIntentHandler {
    private static boolean receivingZip = false;

    @Override
    public boolean needHandleIntent(Intent intent, Activity activity) {
        return intent.hasExtra("zipPassword")
                && !receivingZip
                && activity.getCallingActivity() != null
                && isVerifiedSignature(intent, activity);
    }

    private boolean isVerifiedSignature(Intent intent, Activity activity) {
        PackageInfo callingPackageInfo = PackageUtils.getPackageInfoWithCertificates(activity, activity.getCallingPackage());
        if (AppMigrator.isPtgSignature(callingPackageInfo)) {
            return true;
        }
        byte[] ptgSignatureVerificationByPublicKey = intent.getByteArrayExtra("ptgSignatureVerificationByPublicKey");
        if (ptgSignatureVerificationByPublicKey == null) {
            return false;
        }
        String thumbprint = PackageUtils.getPackageSignatureThumbprint(callingPackageInfo);
        byte[] thumbprintBytes = thumbprint.getBytes(StandardCharsets.UTF_8);
        return AppMigrator.verifyPtgSignatureByPublicKey(thumbprintBytes, ptgSignatureVerificationByPublicKey);
    }

    @Override
    public void handleIntent(Intent intent, Activity activity) {
        receivingZip = true;
        MigrationZipReceiver.receiveZip(activity, intent, error -> onZipReceived(error, activity));
    }

    private void onZipReceived(String error, Activity activity) {
        AndroidUtilities.runOnUIThread(() -> {
            activity.setResult(Activity.RESULT_OK, createZipReceivingResultIntent(error));
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    public static Intent createZipReceivingResultIntent(String error) {
        Intent intent = new Intent();
        intent.putExtra("fromOtherPtg", true);
        intent.putExtra("success", error == null);
        intent.putExtra("error", error);
        intent.putExtra("packageName", ApplicationLoader.applicationContext.getPackageName());
        return intent;
    }
}
