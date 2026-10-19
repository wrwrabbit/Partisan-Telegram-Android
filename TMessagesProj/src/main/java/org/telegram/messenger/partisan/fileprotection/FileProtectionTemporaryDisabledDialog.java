package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.List;

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
        dialog.setCanCancel(true);
        dialog.setCancelable(true);
        dialog.setOnShowListener(d -> {
            dialogShowed = true;
        });
        dialog.setNegativeButton(LocaleController.getString(R.string.Disable), (dlg, which) -> {
            new FileProtectionSwitcher(fragment).apply(false);
        });
        dialog.setPositiveButton(LocaleController.getString(R.string.FileProtectionEnableAgain), (dlg, which) -> {
            if (FileProtectionSettings.fileProtectionForAllAccountsEnabled.get().orElse(true)) {
                new FileProtectionSwitcher(fragment).forceApply(true);
            } else {
                List<FileProtectionAccountInfo> accounts = new ArrayList<>();
                Utils.foreachActivatedAccountInstance(accountInstance ->
                        accounts.add(new FileProtectionAccountInfo(accountInstance.getCurrentAccount()))
                );
                new FileProtectionSwitcher(fragment).apply(accounts, FileProtectionSettings.storeDataInMemoryOnly.get().orElse(true), FileProtectionSettings.storeChatsInMemoryOnly.get().orElse(true), FileProtectionSettings.encryptDatabase.get().orElse(true), FileProtectionSettings.encryptAuthToken.get().orElse(true));
            }
        });
        return dialog;
    }

    public static boolean needShow() {
        if (FakePasscodeUtils.isFakePasscodeActivated() || dialogShowed) {
            return false;
        }
        boolean[] fileProtectionTemporaryDisabledForAnyAccount = new boolean[] {false};
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            if (accountInstance.getMessagesStorage().isFileProtectionDisabledBecauseOfFileSize()) {
                fileProtectionTemporaryDisabledForAnyAccount[0] = true;
            }
        });
        return fileProtectionTemporaryDisabledForAnyAccount[0];
    }
}
