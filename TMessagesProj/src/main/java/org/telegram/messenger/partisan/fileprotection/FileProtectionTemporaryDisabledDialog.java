package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

import java.util.HashMap;
import java.util.Map;

public class FileProtectionTemporaryDisabledDialog {
    private static volatile boolean dialogShowed = false;

    public static AlertDialog createDialogIfNeeded(BaseFragment fragment) {
        if (!needShow()) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.FileProtectionDisabledTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.FileProtectionDisabledDetails)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.Cancel), 5, (dlg, which) -> {
            dialogShowed = true;
        });
        dialog.setNegativeButton(LocaleController.getString(R.string.Disable), (dlg, which) -> {
            new FileProtectionSwitcher(fragment).changeForAllAccounts(false);
            dialogShowed = true;
        });
        dialog.setPositiveButton(LocaleController.getString(R.string.FileProtectionEnableAgain), (dlg, which) -> {
            if (SharedConfig.fileProtectionForAllAccountsEnabled) {
                new FileProtectionSwitcher(fragment).changeForAllAccounts(true);
            } else {
                Map<Integer, Boolean> values = new HashMap<>();
                Utils.foreachActivatedAccountInstance(accountInstance ->
                        values.put(accountInstance.getCurrentAccount(), accountInstance.getUserConfig().fileProtectionEnabled)
                );
                new FileProtectionSwitcher(fragment).changeForMultipleAccounts(values);
            }
            dialogShowed = true;
        });
        return dialog;
    }

    public static boolean needShow() {
        if (FakePasscodeUtils.isFakePasscodeActivated() || dialogShowed) {
            return false;
        }
        boolean[] fileProtectionTemporaryDisabledForAnyAccount = new boolean[] {false};
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            if (accountInstance.getMessagesStorage().fileProtectionDisabledBecauseOfFileSize()) {
                fileProtectionTemporaryDisabledForAnyAccount[0] = true;
            }
        });
        return fileProtectionTemporaryDisabledForAnyAccount[0];
    }
}
