package org.telegram.messenger.partisan.fileprotection;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteDatabaseWrapper;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;

import java.util.HashSet;
import java.util.Set;

public class FileProtectionPostRestartCleaner implements NotificationCenter.NotificationCenterDelegate {
    private final Set<Integer> accountsToClear = new HashSet<>();

    public static void clearDiskChatListIfFileProtectionDisabled(int account, SQLiteDatabase database) {
        if (database instanceof SQLiteDatabaseWrapper || !fileProtectionDisabledAfterRestart(UserConfig.getInstance(account))) {
            return;
        }
        try {
            new FileProtectionDatabaseCleaner(database, account, SQLiteDatabaseWrapper.unionOfActiveBuckets(true)).clear();
        } catch (Exception e) {
            PartisanLog.e("", e);
        }
    }

    private static boolean fileProtectionDisabledAfterRestart(UserConfig userConfig) {
        return userConfig.disableFileProtectionAfterRestart
                || userConfig.disableFileProtectionAfterRestartByFakePasscode
                || FileProtectionSettings.disableFileProtectionAfterRestart.get().orElse(false);
    }

    public void checkAndClean() {
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            UserConfig userConfig = accountInstance.getUserConfig();
            if (fileProtectionDisabledAfterRestart(userConfig)) {
                PartisanLog.d("Clean the database after disabling file protection for account " + accountInstance.getCurrentAccount());
                accountsToClear.add(accountInstance.getCurrentAccount());
                AndroidUtilities.runOnUIThread(() -> {
                    accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.onDatabaseReset);
                    accountInstance.getMessagesStorage().clearLocalDatabase();
                });
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onDatabaseReset) {
            PartisanLog.d("Cleaning the database after disabling file protection for account " + account + " finished");
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.onDatabaseReset);
            UserConfig userConfig = UserConfig.getInstance(account);
            userConfig.disableFileProtectionAfterRestart = false;
            userConfig.disableFileProtectionAfterRestartByFakePasscode = false;
            userConfig.saveConfig(false);
            accountsToClear.remove(account);
            if (accountsToClear.isEmpty()) {
                FileProtectionSettings.disableFileProtectionAfterRestart.set(false);
            }
        }
    }
}
