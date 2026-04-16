package org.telegram.messenger.partisan.ui;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.function.Consumer;
import java.util.function.Function;

public class DangerousSettingSwitcher {
    public BaseFragment fragment;
    public boolean value;
    public Consumer<Boolean> setValue;
    public Consumer<AccountInstance> dangerousAction;
    public Function<UserConfig, Boolean> isChanged;
    public String dangerousActionTitle;
    public String positiveButtonText;
    public String negativeButtonText;
    public String neutralButtonText;
    public Runnable onSettingChanged;

    public void switchSetting() {
        if (fragment == null || fragment.getParentActivity() == null
                || !value || !isChangedSetting(isChanged)) {
            changeSetting(value);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.AppName));
            builder.setMessage(dangerousActionTitle);
            builder.setPositiveButton(positiveButtonText, (dialog, which) -> changeSetting(true));
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> changeSetting(false));
            builder.setNeutralButton(neutralButtonText, null);
            fragment.showDialog(builder.create());
        }
    }

    private void changeSetting(boolean runDangerousAction) {
        setValue.accept(!value);
        SharedConfig.saveConfig();
        if (onSettingChanged != null) {
            onSettingChanged.run();
        }
        if (runDangerousAction) {
            Utils.foreachActivatedAccountInstance(dangerousAction);
        }
    }

    private boolean isChangedSetting(Function<UserConfig, Boolean> isChanged) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated()) {
                if (isChanged.apply(config)) {
                    return true;
                }
            }
        }
        return false;
    }
}
