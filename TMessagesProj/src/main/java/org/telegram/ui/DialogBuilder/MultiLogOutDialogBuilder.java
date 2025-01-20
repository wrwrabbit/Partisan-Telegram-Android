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
        final AlertDialog[] alertDialog = new AlertDialog[1];
        final Set<Integer> selectedAccounts = new HashSet<>();
        final LinearLayout linearLayout = new LinearLayout(fragment.getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        List<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
                selectedAccounts.add(a);
            }
        }
        Collections.sort(accounts, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });
        for (Integer a : accounts) {
            TLRPC.User u = UserConfig.getInstance(a).getCurrentUser();
            if (u != null) {
                int currentAccount = a;
                CheckBoxUserCell cell = new CheckBoxUserCell(fragment.getContext(), false);
                cell.setUser(u, selectedAccounts.contains(a), true);
                cell.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cell.setOnClickListener(v -> {
                    cell.setChecked(!cell.isChecked(), true);
                    if (cell.isChecked()) {
                        selectedAccounts.add(currentAccount);
                    } else {
                        selectedAccounts.remove(currentAccount);
                    }
                });
            }
        }

        builder.setTitle(LocaleController.getString("SelectAccount", R.string.SelectAccount));
        builder.setView(linearLayout);
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
        return alertDialog[0] = builder.create();
    }
}
