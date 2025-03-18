package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;

import org.telegram.messenger.SharedConfig;

public interface IMaskedPasscodeScreenFactory {
    AbstractMaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp);
    boolean allowAlphaNumericPassword();
    boolean allowFingerprint();
    boolean allowIconShortcuts();
    boolean allowCallNotification();
    boolean allowNotHiddenNotifications();
    int getDefaultPrimaryColor();
    default int getDefaultPasscodeType() {
        return SharedConfig.PASSCODE_TYPE_PIN;
    }
}
