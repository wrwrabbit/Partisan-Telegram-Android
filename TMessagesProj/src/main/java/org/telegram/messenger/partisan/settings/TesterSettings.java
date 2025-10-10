package org.telegram.messenger.partisan.settings;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;

import java.util.List;

public class TesterSettings {

    public static final LongSetting updateChannelIdOverride = new LongSetting("updateChannelIdOverride", 0L);
    public static final StringSetting updateChannelUsernameOverride = new StringSetting("updateChannelUsernameOverride", "");
    public static final BooleanSetting premiumDisabled = new BooleanSetting("premiumDisabled", false);
    public static final StringSetting phoneOverride = new StringSetting("phoneOverride", "");
    public static final BooleanSetting forceAllowScreenshots = new BooleanSetting("forceAllowScreenshots", false);
    public static final BooleanSetting saveLogcatAfterRestart = new BooleanSetting("saveLogcatAfterRestart", false);
    public static final BooleanSetting clearLogsWithCache = new BooleanSetting("clearLogsWithCache", true);
    public static final BooleanSetting showEncryptedChatsFromEncryptedGroups = new BooleanSetting("showEncryptedChatsFromEncryptedGroups", false);
    public static final BooleanSetting detailedEncryptedGroupMemberStatus = new BooleanSetting("detailedEncryptedGroupMemberStatus", false);
    public static final BooleanSetting showPlainBackup = new BooleanSetting("showPlainBackup", false);
    public static final BooleanSetting forceSearchDuringDeletion = new BooleanSetting("forceSearchDuringDeletion", false);
    public static final BooleanSetting moreTimerValues = new BooleanSetting("moreTimerValues", false);

    public static void loadSettings() {
        for (Setting<?> setting : getAllSettings()) {
            setting.load();
            setting.setConditionForGet(TesterSettings::areTesterSettingsActivated);
        }
    }

    public static boolean areTesterSettingsActivated() {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return false;
        } else {
            return SharedConfig.activatedTesterSettingType != 0;
        }
    }

    private static List<Setting<?>> getAllSettings() {
        return SettingUtils.getAllSettings(TesterSettings.class);
    }
}