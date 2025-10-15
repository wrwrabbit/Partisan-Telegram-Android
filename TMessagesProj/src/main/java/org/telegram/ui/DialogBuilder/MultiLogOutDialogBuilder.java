package org.telegram.ui.DialogBuilder;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxUserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.FakePasscodeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiLogOutDialogBuilder {
    public static AlertDialog makeLogOutDialog(Context context, int[] accounts) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(LocaleController.getString("AreYouSureLogout", R.string.AreYouSureLogout));
        builder.setTitle(LocaleController.getString("LogOut", R.string.LogOut));
        builder.setPositiveButton(LocaleController.getString("LogOut", R.string.LogOut), (dialogInterface, i) -> {
            for (int account : accounts) {
                MessagesController.getInstance(account).performLogout(1);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
        return alertDialog;
    }

    public static AlertDialog makeMultiLogOutDialog(BaseFragment fragment) {
        if (UserConfig.getActivatedAccountsCount() < 2) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        final Set<Integer> selectedAccounts = new HashSet<>(Utils.getActivatedAccountsSortedByLoginTime());
        LinearLayout accountsLayout = Utils.createAccountsCheckboxLayout(fragment.getContext(), selectedAccounts::contains, (acc, enabled) -> {
            if (enabled) {
                selectedAccounts.add(acc);
            } else {
                selectedAccounts.remove(acc);
            }
        });

        builder.setTitle(LocaleController.getString("SelectAccount", R.string.SelectAccount));
        builder.setView(accountsLayout);
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("LogOut", R.string.LogOut), (dialogInterface, i) -> {
            if (selectedAccounts.isEmpty()) {
                return;
            }
            int[] accountsToLogout = new int[selectedAccounts.size()];
            int added = 0;
            for (int account : selectedAccounts) {
                accountsToLogout[added++] = account;
            }
            fragment.showDialog(makeLogOutDialog(fragment.getContext(), accountsToLogout));
        });
        return builder.create();
    }
}
