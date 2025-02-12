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

import java.util.Set;

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
        MigrationZipReceiver.receiveZip(activity, intent, (error, issues) -> onZipReceived(error, issues, activity));
    }

    private void onZipReceived(String error, Set<String> issues, Activity activity) {
        AndroidUtilities.runOnUIThread(() -> {
            activity.setResult(Activity.RESULT_OK, createZipReceivingResultIntent(error, issues));
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    public static Intent createZipReceivingResultIntent(String error, Set<String> issues) {
        Intent intent = new Intent();
        intent.putExtra("fromOtherPtg", true);
        intent.putExtra("success", error == null);
        intent.putExtra("error", error);
        intent.putExtra("packageName", ApplicationLoader.applicationContext.getPackageName());
        if (issues != null) {
            intent.putExtra("issues", issues.toArray(new String[0]));
        }
        return intent;
    }
}
