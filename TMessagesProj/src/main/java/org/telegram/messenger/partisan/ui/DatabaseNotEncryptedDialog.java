package org.telegram.messenger.partisan.ui;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionDbEncryption;
import org.telegram.messenger.partisan.fileprotection.FileProtectionDbEncryption.PlaintextIssue;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseNotEncryptedDialog {
    private static final Set<Integer> accountsWithDismissedDialog = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static AlertDialog createDialogIfNeeded(BaseFragment fragment) {
        int account = fragment.getCurrentAccount();
        if (!needShow(account)) {
            return null;
        }
        PlaintextIssue issue = FileProtectionDbEncryption.getPlaintextIssue(account);
        if (issue == null) {
            return null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.DatabaseNotEncryptedTitle));
        String settingName = LocaleController.getString(R.string.FileProtectionEncryptDatabase);
        String details = LocaleController.formatString(R.string.DatabaseNotEncryptedDetails, settingName) + "\n\n" + getReasonText(issue);
        builder.setMessage(AndroidUtilities.replaceTags(details));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        dialog.setNegativeButton(LocaleController.getString(R.string.OK), (dlg, which) -> dismissUntilRestart(account, dialog));
        dialog.setPositiveButton(LocaleController.getString(R.string.RestartApplication), (dlg, which) ->
                ProcessPhoenix.triggerRebirth(fragment.getContext())
        );
        return dialog;
    }

    public static boolean needShow(int account) {
        return !FakePasscodeUtils.isFakePasscodeActivated()
                && !accountsWithDismissedDialog.contains(account)
                && FileProtectionDbEncryption.getPlaintextIssue(account) != null;
    }

    private static String getReasonText(PlaintextIssue issue) {
        switch (issue.reason) {
            case NOT_ENOUGH_SPACE:
                return LocaleController.formatString(R.string.DatabaseNotEncryptedNoSpace, AndroidUtilities.formatFileSize(issue.missingSpace));
            case KEY_UNAVAILABLE:
                return LocaleController.getString(R.string.DatabaseNotEncryptedKeyUnavailable);
            default:
                return LocaleController.getString(R.string.DatabaseNotEncryptedFailed);
        }
    }

    private static void dismissUntilRestart(int account, AlertDialog dialog) {
        accountsWithDismissedDialog.add(account);
        dialog.dismiss();
    }
}
