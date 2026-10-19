package org.telegram.messenger.partisan.screenlock;

import android.content.Context;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.Utils;

public class OnScreenLockActions {
    // Persisted in SharedConfig.onScreenLockAction, don't renumber.
    public static final int ACTION_NOTHING = 0;
    public static final int ACTION_HIDE = 1;
    public static final int ACTION_CLOSE = 2;

    public static void executeOnScreenOff(Context context) {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return;
        }
        if (SharedConfig.onScreenLockActionClearCache) {
            Utils.clearCache(context, OnScreenLockActions::hideOrCloseAppIfNeeded);
        } else {
            hideOrCloseAppIfNeeded();
        }
        if (SharedConfig.clearAllDraftsOnScreenLock) {
            Utils.clearAllDrafts();
        }
    }

    private static void hideOrCloseAppIfNeeded() {
        if (SharedConfig.onScreenLockAction == ACTION_HIDE) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.shouldHideApp);
        } else if (SharedConfig.onScreenLockAction == ACTION_CLOSE) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.shouldKillApp);
        }
    }
}
