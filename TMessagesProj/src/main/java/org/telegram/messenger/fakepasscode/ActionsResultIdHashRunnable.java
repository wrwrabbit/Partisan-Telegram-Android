package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.results.ActionsResult;

public class ActionsResultIdHashRunnable implements Runnable {
    private static final int DELAY = 1000;
    private static boolean started = false;

    public static synchronized void start() {
        if (!started) {
            started = true;
            Utilities.globalQueue.postRunnable(new ActionsResultIdHashRunnable(), DELAY);
        }
        Utilities.globalQueue.postRunnable(ActionsResultIdHashRunnable::checkNow);
    }

    private static void checkNow() {
        ActionsResult actionsResult = SharedConfig.fakePasscodeActionsResult;
        if (actionsResult != null) {
            actionsResult.checkIdHashes();
        }
    }

    @Override
    public void run() {
        checkNow();
        Utilities.globalQueue.postRunnable(this, DELAY);
    }
}
