package org.telegram.messenger.partisan;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;

public class PartisanLog {
    public static void handleException(Exception e) {
        e("error", e);
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            throw new Error(e);
        }
    }

    public static void e(final String message) {
        if (logsAllowed()) {
            FileLog.e(message);
        }
    }

    public static void e(final Throwable exception) {
        if (logsAllowed()) {
            FileLog.e(exception);
        }
    }

    public static void e(final String message, final Throwable exception) {
        if (logsAllowed()) {
            FileLog.e(message, exception);
        }
    }

    public static void d(final String message) {
        if (logsAllowed()) {
            FileLog.d(message);
        }
    }

    public static boolean logsAllowed() {
        try {
            return isLogsEnabled() && isTesterSettingsActivated();
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean isLogsEnabled() {
        if (BuildVars.LOGS_ENABLED) {
            return true;
        }
        if (SharedConfig.isConfigLoaded()) {
            return false;
        }
        return getSharedPreferences().getBoolean("logsEnabled", BuildVars.DEBUG_VERSION);
    }

    private static boolean isTesterSettingsActivated() {
        if (SharedConfig.activatedTesterSettingType != 0 || true) {
            return true;
        }
        if (SharedConfig.isConfigLoaded()) {
            return false;
        }
        return getSharedPreferences().getInt("activatedTesterSettingType", BuildVars.DEBUG_PRIVATE_VERSION ? 1 : 0) != 0;
    }

    private static SharedPreferences getSharedPreferences() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
    }
}
