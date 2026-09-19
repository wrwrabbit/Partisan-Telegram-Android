package org.telegram.messenger.partisan.appmigration;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

public class AppMigrationDialogs {
    public static boolean needShowNewerPtgDialog(Context context) {
        return !FakePasscodeUtils.isFakePasscodeActivated()
                && !AppMigrator.isMigrationStarted()
                && !MigrationConnectionDisabler.isConnectionDisabled()
                && targetPtgPackageInstalled(context);
    }

    private static boolean targetPtgPackageInstalled(Context context) {
        return AppMigrator.isNewerPtgInstalled(context, true)
                || AppMigratorPreferences.isMigrationToMaskedPtg();
    }

    public static AlertDialog createNewerPtgInstalledDialog(BaseFragment fragment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.OtherPTelegramAlertTitle));
        builder.setMessage(LocaleController.getString(R.string.OtherPTelegramAlert));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dlg, which) -> {
            if (AppMigratorPreferences.isMigrationToMaskedPtg()) {
                AppMigratorPreferences.setInstalledMaskedPtgPackageName(null);
                AppMigratorPreferences.setInstalledMaskedPtgPackageSignature(null);
                AppMigratorPreferences.setStep(Step.NOT_STARTED);
            } else {
                AppMigratorPreferences.updateMaxCancelledInstallationDate();
            }
        });
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dlg, which) ->
                fragment.presentFragment(new AppMigrationActivity()));
        return builder.create();
    }

    public static AlertDialog createUpdateCompletedDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.UpdateCompletedTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.UpdateCompletedMessage)));
        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        return dialog;
    }

    public static AlertDialog createOldPtgNotRemovedDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.OldAppNotRemovedTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.OldAppNotRemovedMessage)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.OK), 10,
                (dlg, which) -> dlg.dismiss());
        return dialog;
    }
}
