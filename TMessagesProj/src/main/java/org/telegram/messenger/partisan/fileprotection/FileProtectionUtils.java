package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.UserConfig;

public class FileProtectionUtils {
    public static boolean encryptionEnabledByConfig(int account) {
        return FileProtectionSettings.encryptDatabase.get().orElse(true) && fileProtectionEnabledForAccount(account);
    }

    public static boolean fileProtectionEnabledForAccount(int account) {
        if (FileProtectionSettings.fileProtectionForAllAccountsEnabled.get().orElse(true)) {
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
