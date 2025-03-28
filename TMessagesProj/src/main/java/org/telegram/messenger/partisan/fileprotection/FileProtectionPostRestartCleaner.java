package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;

import java.util.HashSet;
import java.util.Set;

public class FileProtectionPostRestartCleaner implements NotificationCenter.NotificationCenterDelegate {
    private final Set<Integer> accountsToClear = new HashSet<>();

    public void checkAndClean() {
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            UserConfig userConfig = accountInstance.getUserConfig();
            if (userConfig.disableFileProtectionAfterRestart || userConfig.disableFileProtectionAfterRestartByFakePasscode || SharedConfig.disableFileProtectionAfterRestart) {
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
                SharedConfig.setDisableFileProtectionAfterRestart(false);
            }
        }
    }
}
