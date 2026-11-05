package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.UserConfig;

public class FileProtectionUtils {
    public static boolean encryptionEnabledByConfig(int account) {
        return FileProtectionSettings.encryptDatabase.get().orElse(true) && fileProtectionEnabledForAccount(account)
                && isSlotInUse(account);
    }

    public static boolean authTokenEncryptionEnabledByConfig(int account) {
        return authTokenEncryptionEnabledForSlot(account) && isSlotInUse(account);
    }

    static boolean authTokenEncryptionEnabledForSlot(int account) {
        return FileProtectionSettings.encryptAuthToken.get().orElse(true) && fileProtectionEnabledForAccount(account);
    }

    public static boolean isSlotInUse(int account) {
        UserConfig userConfig = UserConfig.getInstance(account);
        userConfig.loadConfig();
        return userConfig.isClientActivated() || FileProtectionTgnetEncryption.isLoginInProgress(account);
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

    public static boolean fileProtectionEnabledForAnyAccount() {
        return anyProtectionFeatureEnabled() && getAccountsWithFileProtectionCount() > 0;
    }

    private static boolean anyProtectionFeatureEnabled() {
        return FileProtectionSettings.storeDataInMemoryOnly.get().orElse(true)
                || FileProtectionSettings.encryptDatabase.get().orElse(true)
                || FileProtectionSettings.encryptAuthToken.get().orElse(true);
    }

    private static int getAccountsWithFileProtectionCount() {
        if (FileProtectionSettings.fileProtectionForAllAccountsEnabled.get().orElse(true)) {
            return UserConfig.getActivatedAccountsCount();
        }
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() && config.fileProtectionEnabled) {
                count++;
            }
        }
        return count;
    }
}
