package org.telegram.messenger.partisan.appmigration;

import android.content.pm.PackageInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MaskedMigratorHelper {
    public static String MASKING_BOT_USERNAME = "MaskedPtgBot";
    public static final long MASKING_BOT_ID = 7901437172L;
    private static final long MASKING_BOT_ID2 = 7138739692L;
    private static final long MASKING_BOT_ID3 = 7116474629L;
    private static final Map<File, PackageInfo> fileToPackageInfo = new HashMap<>();
    private static PackageInfo installingPackageInfo = null;
    private static Set<MaskedMigrationIssue> migrationIssues = new HashSet<>();

    public static void saveFileMetadataFromMaskingBotIfNeed(File f, MessageObject message) {
        if (message.messageOwner.from_id.user_id == MASKING_BOT_ID
                || message.messageOwner.from_id.user_id == MASKING_BOT_ID2
                || message.messageOwner.from_id.user_id == MASKING_BOT_ID3) {
            PackageInfo packageInfo = PackageUtils.extractPackageInfoFromFile(f);
            if (packageInfo != null) {
                fileToPackageInfo.put(f, packageInfo);
            }
        }
    }

    public static boolean ifFileFromMaskingBot(File f) {
        return fileToPackageInfo.containsKey(f);
    }

    public static void onStartInstallingAppFromMaskingBot(File f) {
        installingPackageInfo = fileToPackageInfo.get(f);
    }

    public static boolean saveAppFromMaskingBotInstalled() {
        if (installingPackageInfo == null) {
            return false;
        }
        AppMigratorPreferences.setInstalledMaskedPtgPackageName(installingPackageInfo.applicationInfo.packageName);
        AppMigratorPreferences.setInstalledMaskedPtgPackageSignature(PackageUtils.getPackageSignatureThumbprint(installingPackageInfo));
        return true;
    }

    public static String getInstallingPackageName() {
        if (installingPackageInfo == null) {
            return null;
        }
        return installingPackageInfo.applicationInfo.packageName;
    }

    public static synchronized void setMigrationIssues(String[] issues) {
        try {
            migrationIssues = Arrays.asList(issues).stream()
                    .map(MaskedMigrationIssue::valueOf)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException ignore) {
            migrationIssues = new HashSet<>();
        }
    }

    public static synchronized void removeMigrationIssueAndShowDialogIfNeeded(BaseFragment fragment, MaskedMigrationIssue issue) {
        if (migrationIssues.remove(issue)) {
            if (migrationIssues.isEmpty()) {
                showMigrationCanContinueDialog(fragment);
            }
        }
    }

    private static void showMigrationCanContinueDialog(BaseFragment fragment) {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.MigrationTitle));
        builder.setMessage(LocaleController.getString(R.string.ContinueMaskedMigrationDescription));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialog1, which) -> {
            fragment.presentFragment(new AppMigrationActivity());
        });
        builder.setNeutralButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        dialog.setCanDismissDialogOnPause(false);
        fragment.showDialog(dialog);
    }
}
