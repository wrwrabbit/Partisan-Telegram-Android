package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;

public class FileProtectionAccountInfo {
    public int accountNum;
    public boolean fileProtectionEnabled;

    public FileProtectionAccountInfo(int accountNum) {
        this.accountNum = accountNum;
        this.fileProtectionEnabled = SharedConfig.fileProtectionForAllAccountsEnabled
                || getUserConfig().fileProtectionEnabled;
    }

    public UserConfig getUserConfig() {
        return UserConfig.getInstance(accountNum);
    }
}
