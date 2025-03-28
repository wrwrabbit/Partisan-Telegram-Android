package org.telegram.messenger.partisan.masked_ptg.note;

import android.content.Context;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_ptg.IMaskedPasscodeScreenFactory;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;

public class NoteScreenFactory implements IMaskedPasscodeScreenFactory {
    @Override
    public AbstractMaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        return new NotePasscodeScreen(context, delegate, unlockingApp);
    }

    @Override
    public boolean allowAlphaNumericPassword() {
        return true;
    }

    @Override
    public boolean allowFingerprint() {
        return false;
    }

    @Override
    public boolean allowIconShortcuts() {
        return false;
    }

    @Override
    public boolean allowCallNotification() {
        return true;
    }

    @Override
    public boolean allowNotHiddenNotifications() {
        return false;
    }

    @Override
    public int getDefaultPrimaryColor() {
        return Colors.primaryColor;
    }

    @Override
    public int getDefaultPasscodeType() {
        return SharedConfig.PASSCODE_TYPE_PASSWORD;
    }
}
