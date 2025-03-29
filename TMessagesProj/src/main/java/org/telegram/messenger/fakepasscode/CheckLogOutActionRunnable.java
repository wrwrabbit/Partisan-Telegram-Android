package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.Utils;

public class CheckLogOutActionRunnable implements Runnable {
    private static final int DELAY = 500;
    private static final int THRESHOLD = 5000;

    private final LogOutAction action;
    private final FakePasscode fakePasscode;
    private int i = 0;

    public CheckLogOutActionRunnable(LogOutAction action, FakePasscode fakePasscode) {
        this.action = action;
        this.fakePasscode = fakePasscode;
    }

    @Override
    public void run() {
        if (fakePasscode.actionsResult.actionsPreventsLogoutAction.isEmpty() || i == THRESHOLD / DELAY) {
            Utils.runOnUIThreadAsSoonAsPossible(() -> {
                action.execute(fakePasscode);
                fakePasscode.checkClearAfterActivation();
            });
        } else {
            if (i == 0) {
                AndroidUtilities.runOnUIThread(() -> action.hideAccount(fakePasscode));
            }
            i++;
            Utilities.globalQueue.postRunnable(this, DELAY);
        }
    }
}
