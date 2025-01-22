package org.telegram.messenger.partisan.fileprotection;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

public class FileProtectionNewFeatureDialog {
    public static void showDialogIfNeeded(BaseFragment fragment) {
        if (!needShow()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.NewPtelegramFeatureTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.FileProtectionNewFeatureDetails)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.Cancel), 5, (dlg, which) -> {
            deleteNeedShowPreference();
            dlg.dismiss();
        });
        dialog.setPositiveButton(LocaleController.getString(R.string.Enable), (dlg, which) -> {
            new FileProtectionSwitcher(fragment).changeForAllAccounts(true);
            deleteNeedShowPreference();
        });
        fragment.showDialog(dialog);
    }

    public static boolean needShow() {
        return !FakePasscodeUtils.isFakePasscodeActivated() && needShowPreferenceExists();
    }

    private static boolean needShowPreferenceExists() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
        return preferences.contains("needShowFileProtectionNewFeatureDialog");
    }

    private static void deleteNeedShowPreference() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("needShowFileProtectionNewFeatureDialog");
        editor.apply();
    }
}
