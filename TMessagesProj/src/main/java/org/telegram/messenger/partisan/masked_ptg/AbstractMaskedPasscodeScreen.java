package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

public abstract class AbstractMaskedPasscodeScreen {
    protected final PasscodeEnteredDelegate delegate;
    protected final Context context;
    protected TutorialType tutorialType = TutorialType.DISABLED;
    private final boolean unlockingApp;

    public AbstractMaskedPasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        this.context = context;
        this.delegate = delegate;
        this.unlockingApp = unlockingApp;
    }

    public abstract View createView();
    public abstract void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType);
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {}
    public void onPasscodeError() {}
    public void onAttachedToWindow() {}
    public void onDetachedFromWindow() {}
    public boolean onBackPressed () {
        return true;
    }

    protected AlertDialog createMaskedPasscodeScreenInstructionDialog(String message, int okButtonTimeout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        int titleRes = tutorialType == TutorialType.FULL
                ? R.string.MaskedPasscodeScreenTrainingTitle
                : R.string.MaskedPasscodeScreenInstructionTitle;
        builder.setTitle(LocaleController.getString(titleRes));
        String finalMessage = message + "\n\n" + LocaleController.getString(R.string.MaskedPasscodeScreenInstructionShownOnlyOnce);
        builder.setMessage(finalMessage);
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        if (okButtonTimeout != 0) {
            DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_POSITIVE, LocaleController.getString(R.string.OK), 5,
                    (dlg, which) -> dlg.dismiss());
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        }
        if (unlockingApp) {
            builder.setNegativeButton(LocaleController.getString(R.string.MaskedPasscodeScreenDisablePasscode), (dlg, which) -> {
                dlg.dismiss();
                createPasscodeDisableConfirmationDialog().show();
            });
        }
        return dialog;
    }

    protected AlertDialog createPasscodeDisableConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreenDisablePasscode));
        String message = LocaleController.formatString(R.string.MaskedPasscodeScreenDisablePasscodeMessage,
                LocaleController.getString(R.string.MaskedPasscodeScreenDisablePasscode));
        builder.setMessage(message);
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        builder.setPositiveButton(LocaleController.getString(R.string.MaskedPasscodeScreenDisablePasscode), (dlg, which) -> {
            SharedConfig.setPasscode("");
            for (FakePasscode passcode: SharedConfig.fakePasscodes) {
                passcode.onDelete();
            }
            SharedConfig.fakePasscodes.clear();
            SharedConfig.needShowMaskedPasscodeScreenTutorial = false;
            SharedConfig.saveConfig();
            delegate.passcodeEntered("");
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        return dialog;
    }
}
