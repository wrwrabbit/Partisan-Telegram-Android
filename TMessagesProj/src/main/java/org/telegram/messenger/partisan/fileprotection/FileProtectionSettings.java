package org.telegram.messenger.partisan.fileprotection;

import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.messenger.partisan.settings.Setting;
import org.telegram.messenger.partisan.settings.SettingUtils;

public class FileProtectionSettings {
    public static final BooleanSetting fileProtectionForAllAccountsEnabled = new BooleanSetting("fileProtectionForAllAccountsEnabled", true);
    public static final BooleanSetting disableFileProtectionAfterRestart = new BooleanSetting("disableFileProtectionAfterRestart", false);
    public static final BooleanSetting storeMessagesInMemoryOnly = new BooleanSetting("dontStoreMessagesOnDevice", true);
    public static final BooleanSetting encryptDatabase = new BooleanSetting("encryptDatabaseEnabled", true);
    public static final BooleanSetting fileProtectionWorksWhenFakePasscodeActivated = new BooleanSetting("fileProtectionWorksWhenFakePasscodeActivated", true);

    public static void loadSettings() {
        for (Setting<?> setting : SettingUtils.getAllSettings(FileProtectionSettings.class)) {
            setting.setPreferencesName("mainconfig");
            setting.load();
        }
    }
}
