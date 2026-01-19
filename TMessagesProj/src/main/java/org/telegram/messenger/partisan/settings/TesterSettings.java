package org.telegram.messenger.partisan.settings;

import android.util.Pair;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.nio.charset.StandardCharsets;
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
    public static final BooleanSetting showTesterSettingsWithFakePasscode = new BooleanSetting("showTesterSettingsWithFakePasscode", false);

    public static void loadSettings() {
        for (Setting<?> setting : getAllSettings()) {
            setting.load();
            if (setting != phoneOverride && setting != forceAllowScreenshots) {
                setting.setConditionForGet(TesterSettings::areTesterSettingsActivated);
            }
        }
    }

    public static boolean areTesterSettingsActivated() {
        return SharedConfig.activatedTesterSettingType != 0;
    }

    private static List<Setting<?>> getAllSettings() {
        return SettingUtils.getAllSettings(TesterSettings.class);
    }

    public static void checkTesterSettingsPassword(String password) {
        try {
            final String SALT = "|_}H<{&U.?0c43*krr*bVFH6xt1Y`L}'";
            List<Pair<String, Integer>> PASSWORD_HASHES_AND_SETTING_TYPES = List.of(
                    new Pair<>("50FB2E837B1111E4F978D60AFC549F7B130AE65C455E9C04800357F9B06149BA", 2),
                    new Pair<>("F4500E50A43264F10042EFEC9209F0DA89C4B17EF5F6982B5EFC8CEDC9E2C836", 3),
                    new Pair<>("1F9A8AF5C7B0CFC4CB056E8B7F0ECDB301FD83105308BBAF4759A1B263378697", 1)
            );

            byte[] bytes = (SALT + password + SALT).getBytes(StandardCharsets.UTF_8);
            String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));

            SharedConfig.activatedTesterSettingType = 0;
            for (Pair<String, Integer> pair : PASSWORD_HASHES_AND_SETTING_TYPES) {
                if (hash.equals(pair.first)) {
                    SharedConfig.activatedTesterSettingType = pair.second;
                }
            }
            SharedConfig.saveConfig();
        } catch (Exception ignored) {
        }
    }

    public static boolean areVoiceChangingSettingsVisible() {
        return SharedConfig.activatedTesterSettingType >= 2;
    }
}