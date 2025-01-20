package org.telegram.messenger.partisan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

public class FileProtectionNewFeatureDialog {
    public static volatile boolean needShowDialog = false;

    public static void showDialogIfNeeded(BaseFragment fragment) {
        if (!needShowDialog || FakePasscodeUtils.isFakePasscodeActivated()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.NewPtelegramFeatureTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.FileProtectionNewFeatureDetails)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.Cancel), 5,
                (dlg, which) -> dlg.dismiss());
        dialog.setPositiveButton(LocaleController.getString(R.string.Enable), (dlg, which) -> new FileProtectionSwitcher(fragment).changeForAllAccounts(true));
        fragment.showDialog(dialog);
    }
}
