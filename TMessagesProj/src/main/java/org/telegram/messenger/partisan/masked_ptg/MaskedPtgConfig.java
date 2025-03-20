package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import org.telegram.messenger.partisan.masked_ptg.original.OriginalScreenFactory;

public class MaskedPtgConfig {
    private static final Integer PRIMARY_COLOR = null;
    private static final IMaskedPasscodeScreenFactory FACTORY = new OriginalScreenFactory();

    public static AbstractMaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        return FACTORY.createScreen(context, delegate, unlockingApp);
    }

    public static boolean allowAlphaNumericPassword() {
        return FACTORY.allowAlphaNumericPassword();
    }

    public static boolean allowFingerprint() {
        return FACTORY.allowFingerprint();
    }

    public static boolean allowIconShortcuts() {
        return FACTORY.allowIconShortcuts();
    }

    public static boolean allowCallNotification() {
        return FACTORY.allowCallNotification();
    }

    public static boolean allowNotHiddenNotifications() {
        return FACTORY.allowNotHiddenNotifications();
    }

    public static int getDefaultPasscodeType() {
        return FACTORY.getDefaultPasscodeType();
    }

    public static int getPrimaryColor(Context context) {
        if (PRIMARY_COLOR != null) {
            return PRIMARY_COLOR;
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                TypedValue typedValue = new TypedValue();
                ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault);
                if (contextThemeWrapper.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
                    return typedValue.data;
                }
            }
            return FACTORY.getDefaultPrimaryColor();
        }
    }
}
