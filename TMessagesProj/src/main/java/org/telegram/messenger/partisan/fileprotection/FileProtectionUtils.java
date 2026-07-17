package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;

public class FileProtectionUtils {
    public static boolean encryptionEnabledByConfig(int account) {
        return SharedConfig.encryptDatabase && fileProtectionEnabledForAccount(account);
    }

    public static boolean fileProtectionEnabledForAccount(int account) {
        if (SharedConfig.fileProtectionForAllAccountsEnabled) {
            return true;
        }
        UserConfig userConfig = UserConfig.getInstance(account);
        if (userConfig.isConfigLoaded()) {
            return userConfig.fileProtectionEnabled;
        } else {
            return userConfig.getPreferences().getBoolean("fileProtectionEnabled", false);
        }
    }
}
