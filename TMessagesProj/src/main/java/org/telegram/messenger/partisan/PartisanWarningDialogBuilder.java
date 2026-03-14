package org.telegram.messenger.partisan;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.appmigration.AppMigrator;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.function.Supplier;

// This approach may seem strange, but it allows us not to move or duplicate the original code,
// but to wrap it in a positive runnable.
public class PartisanWarningDialogBuilder {
    private Supplier<Boolean> condition;
    private BaseFragment fragment;
    private Context context;
    private String title;
    private String message;
    private String buttonText;
    private Runnable onAccepted;

    private PartisanWarningDialogBuilder(BaseFragment fragment, Runnable onAccepted) {
        this.fragment = fragment;
        if (fragment != null) {
            this.context = fragment.getContext();
        }
        this.onAccepted = onAccepted;
    }

    private PartisanWarningDialogBuilder(Context context, Runnable onAccepted) {
        this.context = context;
        this.onAccepted = onAccepted;
    }

    public static void showConfirmDangerousActionDialogIfNeeded(BaseFragment fragment, Runnable onAccepted) {
        showConfirmDangerousActionDialogIfNeeded(fragment, true, onAccepted);
    }

    public static void showConfirmDangerousActionDialogIfNeeded(BaseFragment fragment, boolean dialogShowingAllowed, Runnable onAccepted) {
        PartisanWarningDialogBuilder builder = new PartisanWarningDialogBuilder(fragment, onAccepted);
        builder.condition = () -> SharedConfig.confirmDangerousActions
                && !FakePasscodeUtils.isFakePasscodeActivated()
                && dialogShowingAllowed;
        builder.title = LocaleController.getString(R.string.ConfirmAction);
        builder.message = LocaleController.getString(R.string.ConfirmDangerousActionAlertInfo);
        builder.buttonText = LocaleController.getString(R.string.OK);
        builder.showDialogIfNeeded();
    }

    public static void showCantChangePhoneNumberDialogIfNeeded(BaseFragment fragment, Runnable onAccepted) {
        PartisanWarningDialogBuilder builder = new PartisanWarningDialogBuilder(fragment, onAccepted);
        builder.condition = () -> !FakePasscodeUtils.isFakePasscodeActivated();
        builder.title = LocaleController.getString(R.string.CantChangePhoneNumberTitle);
        builder.message = LocaleController.getString(R.string.CantChangePhoneNumberDescription);
        builder.showDialogIfNeeded();
    }

    public static void showConnectionDisabledDialogIfNeeded(BaseFragment fragment, Runnable onAccepted) {
        PartisanWarningDialogBuilder builder = new PartisanWarningDialogBuilder(fragment, onAccepted);
        builder.condition = () -> AppMigrator.isConnectionDisabled()
                && !FakePasscodeUtils.isFakePasscodeActivated();
        builder.title = LocaleController.getString(R.string.ConnectionDisabledTitle);
        builder.message = LocaleController.getString(R.string.ConnectionDisabledMessage);
        builder.buttonText = LocaleController.getString(R.string.Continue);
        builder.showDialogIfNeeded();
    }

    public static void showCantSetupPasskeysIfNeeded(BaseFragment fragment, Runnable onAccepted) {
        showCantSetupPasskeysIfNeededInternal(new PartisanWarningDialogBuilder(fragment, onAccepted));
    }

    public static void showCantSetupPasskeysIfNeeded(Context context, Runnable onAccepted) {
        showCantSetupPasskeysIfNeededInternal(new PartisanWarningDialogBuilder(context, onAccepted));
    }

    private static void showCantSetupPasskeysIfNeededInternal(PartisanWarningDialogBuilder builder) {
        builder.condition = () -> !FakePasscodeUtils.isFakePasscodeActivated();
        builder.title = LocaleController.getString(R.string.CantSetupPasskeys);
        builder.message = LocaleController.getString(R.string.CantSetupPasskeysDescription);
        builder.showDialogIfNeeded();
    }

    private void showDialogIfNeeded() {
        if (condition.get()) {
            if (fragment == null && context == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(title);
            builder.setMessage(message);
            if (buttonText != null) {
                builder.setPositiveButton(LocaleController.getString(R.string.Continue),
                        (dialog2, which) -> onAccepted.run()
                );
            }
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            if (fragment != null) {
                fragment.showDialog(builder.create());
            } else {
                builder.create().show();
            }
        } else if (onAccepted != null) {
            onAccepted.run();
        }
    }
}
