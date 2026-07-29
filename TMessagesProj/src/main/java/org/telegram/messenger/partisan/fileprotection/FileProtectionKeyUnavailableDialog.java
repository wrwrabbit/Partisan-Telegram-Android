package org.telegram.messenger.partisan.fileprotection;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionTgnetEncryption.ConfigKeyState;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileProtectionKeyUnavailableDialog {
    private static final Set<Integer> accountsWithDismissedDialog = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static AlertDialog createDialogIfNeeded(BaseFragment fragment) {
        int account = fragment.getCurrentAccount();
        if (!needShow(account)) {
            return null;
        }
        if (FileProtectionTgnetEncryption.getConfigKeyState(account) == ConfigKeyState.EXISTING_KEY_UNREADABLE) {
            return createDataUnavailableDialog(fragment, account);
        } else {
            return createDataCannotBeProtectedDialog(fragment, account);
        }
    }

    public static boolean needShow(int account) {
        return !FakePasscodeUtils.isFakePasscodeActivated()
                && !accountsWithDismissedDialog.contains(account)
                && FileProtectionTgnetEncryption.getConfigKeyState(account) != ConfigKeyState.READY;
    }

    private static AlertDialog createDataUnavailableDialog(BaseFragment fragment, int account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AccountDataUnavailableTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AccountDataUnavailableDetails)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        if (FileProtectionTgnetEncryption.isConfigKeyUnreadableAcrossRestarts(account)) {
            dialog.setPositiveButton(LocaleController.getString(R.string.Cancel), (dlg, which) -> dismissUntilRestart(account, dlg));
            DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.LogOut), 5, (dlg, which) ->
                    MessagesController.getInstance(account).performLogout(2)
            );
        } else {
            dialog.setNegativeButton(LocaleController.getString(R.string.Cancel), (dlg, which) -> dismissUntilRestart(account, dlg));
            dialog.setPositiveButton(LocaleController.getString(R.string.RestartApplication), (dlg, which) ->
                    ProcessPhoenix.triggerRebirth(fragment.getContext())
            );
        }
        return dialog;
    }

    private static AlertDialog createDataCannotBeProtectedDialog(BaseFragment fragment, int account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AccountDataCannotBeProtectedTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AccountDataCannotBeProtectedDetails)));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.KeepProtection), 5, (dlg, which) ->
                dismissUntilRestart(account, dialog)
        );
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_POSITIVE, LocaleController.getString(R.string.ContinueWithoutProtection), 5, (dlg, which) -> {
            FileProtectionSettings.storeAuthTokenUnencryptedWhenKeyUnavailable.set(true);
            ProcessPhoenix.triggerRebirth(fragment.getContext());
        });
        return dialog;
    }

    private static void dismissUntilRestart(int account, AlertDialog dialog) {
        accountsWithDismissedDialog.add(account);
        dialog.dismiss();
    }
}
