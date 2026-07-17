package org.telegram.messenger.partisan.fileprotection;

import android.content.Context;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileProtectionSwitcher implements NotificationCenter.NotificationCenterDelegate {
    private final Set<Integer> accountsWithEnabledFileProtection = new HashSet<>();
    private final BaseFragment fragment;
    private boolean enableForAllAccounts;
    private List<FileProtectionAccountInfo> valuesPerAccounts;
    private boolean forceApply;
    private boolean storeMessagesInMemoryOnly;
    private boolean encryptDatabase;

    public FileProtectionSwitcher(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public void apply(boolean enableForAllAccounts) {
        applyForAllAccounts(enableForAllAccounts);
    }

    public void forceApply(boolean enableForAllAccounts) {
        forceApply = true;
        applyForAllAccounts(enableForAllAccounts);
    }

    private void applyForAllAccounts(boolean enableForAllAccounts) {
        this.enableForAllAccounts = enableForAllAccounts;
        valuesPerAccounts = new ArrayList<>();
        storeMessagesInMemoryOnly = SharedConfig.storeMessagesInMemoryOnly;
        encryptDatabase = SharedConfig.encryptDatabase;
        startSwitching();
    }

    // accounts must cover every activated account
    public void apply(List<FileProtectionAccountInfo> accounts, boolean storeMessagesInMemoryOnly, boolean encryptDatabase) {
        this.storeMessagesInMemoryOnly = storeMessagesInMemoryOnly;
        this.encryptDatabase = encryptDatabase;
        setProtectedAccounts(accounts);
        startSwitching();
    }

    private void setProtectedAccounts(List<FileProtectionAccountInfo> accounts) {
        if (!allActivatedAccountsPassed(accounts)) {
            throw new IllegalArgumentException("accounts must include every activated account; partial lists are not supported");
        }
        if (accounts.stream().allMatch(account -> account.fileProtectionEnabled)) {
            enableForAllAccounts = true;
            valuesPerAccounts = new ArrayList<>();
        } else if (accounts.stream().allMatch(account -> !account.fileProtectionEnabled)) {
            enableForAllAccounts = false;
            valuesPerAccounts = new ArrayList<>();
        } else {
            enableForAllAccounts = false;
            valuesPerAccounts = accounts;
        }
    }

    private static boolean allActivatedAccountsPassed(List<FileProtectionAccountInfo> accounts) {
        return Utils.getActivatedAccountsSortedByLoginTime().stream()
                .allMatch(activatedAcc -> accounts.stream()
                        .anyMatch(account -> account.accountNum == activatedAcc)
                );
    }

    public static boolean fileProtectedAccountsChanged(List<FileProtectionAccountInfo> accounts) {
        for (FileProtectionAccountInfo account : accounts) {
            boolean current = SharedConfig.fileProtectionForAllAccountsEnabled
                    || UserConfig.getInstance(account.accountNum).fileProtectionEnabled;
            if (account.fileProtectionEnabled != current) {
                return true;
            }
        }
        return false;
    }

    private boolean fileProtectedAccountsChangedInternal() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated()) {
                boolean current = SharedConfig.fileProtectionForAllAccountsEnabled || config.fileProtectionEnabled;
                if (needEnableFileProtectionForAccount(a) != current) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startSwitching() {
        if (!needClearLocalDb()) {
            updateConfigs();
            ProcessPhoenix.triggerRebirth(getContext());
            return;
        }

        AlertDialog enablingFileProtectionDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        fragment.showDialog(enablingFileProtectionDialog);

        accountsWithEnabledFileProtection.clear();
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            if (needEnableFileProtectionForAccount(accountInstance.getCurrentAccount())) {
                accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.onDatabaseReset);
                accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.onFileProtectedDbCleared);
                accountInstance.getMessagesStorage().clearLocalDatabase();
            }
        });
    }

    private boolean needClearLocalDb() {
        return !onlyDbEncryptionChanged()
                && storeMessagesInMemoryOnly
                && (enableForAllAccounts || !valuesPerAccounts.isEmpty());
    }

    private boolean onlyDbEncryptionChanged() {
        return !forceApply && !fileProtectedAccountsChangedInternal()
                && storeMessagesInMemoryOnly == SharedConfig.storeMessagesInMemoryOnly;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onDatabaseReset) {
            MessagesStorage.getInstance(account).clearFileProtectedDb();
        } else if (id == NotificationCenter.onFileProtectedDbCleared) {
            accountsWithEnabledFileProtection.add(account);
            if (fileProtectionEnablingFinished()) {
                updateConfigs();
                ProcessPhoenix.triggerRebirth(getContext());
            }
        }
    }

    private void updateConfigs() {
        SharedConfig.setEncryptDatabase(encryptDatabase);
        if (onlyDbEncryptionChanged()) {
            return;
        }
        SharedConfig.setStoreMessagesInMemoryOnly(storeMessagesInMemoryOnly);
        if (SharedConfig.fileProtectionForAllAccountsEnabled) {
            SharedConfig.setDisableFileProtectionAfterRestart(true);
        }
        SharedConfig.setFileProtectionForAllAccounts(enableForAllAccounts);
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            boolean enabledInConfig = isEnabledInValuesPerAccounts(accountInstance.getCurrentAccount());
            boolean enabledForAccountOrGlobally = SharedConfig.fileProtectionForAllAccountsEnabled
                    || enabledInConfig;
            UserConfig userConfig = accountInstance.getUserConfig();
            if (userConfig.fileProtectionEnabled != enabledForAccountOrGlobally) {
                userConfig.clearPinnedDialogsLoaded();
            }
            if (userConfig.fileProtectionEnabled && !enabledInConfig) {
                userConfig.disableFileProtectionAfterRestart = true;
            }
            userConfig.fileProtectionEnabled = enabledInConfig;
            userConfig.saveConfig(false);
        });
    }

    private boolean fileProtectionEnablingFinished() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() && needEnableFileProtectionForAccount(a) && !accountsWithEnabledFileProtection.contains(a)) {
                return false;
            }
        }
        return true;
    }

    private boolean needEnableFileProtectionForAccount(int accountNum) {
        return enableForAllAccounts || isEnabledInValuesPerAccounts(accountNum);
    }

    private boolean isEnabledInValuesPerAccounts(int accountNum) {
        return valuesPerAccounts.stream()
                .anyMatch(account -> account.accountNum == accountNum && account.fileProtectionEnabled);
    }

    private Context getContext() {
        return fragment.getContext();
    }
}
