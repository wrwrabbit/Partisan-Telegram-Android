package org.telegram.messenger.partisan.settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
    public static final FloatSetting pitchFactor = new FloatSetting("pitchFactor", 1.0f);
    public static final FloatSetting timeStretchFactor = new FloatSetting("timeStretchFactor", 1.0f);
    public static final StringSetting spectrumDistorterParams = new StringSetting("spectrumDistorterParams", "");
    public static final StringSetting timeDistortionParams = new StringSetting("timeDistortionParams", "");
    public static final FloatSetting f0Shift = new FloatSetting("f0Shift", 1.0f);
    public static final FloatSetting formantRatio = new FloatSetting("formantRatio", 1.0f);

    public static void loadSettings() {
        for (Setting<?> setting : getAllSettings()) {
            setting.load();
        }
    }

    private static List<Setting<?>> getAllSettings() {
        List<Setting<?>> settings = new ArrayList<>();
        for (Field field : TesterSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Setting.class.isAssignableFrom(field.getType())) {
                try {
                    settings.add((Setting<?>) field.get(TesterSettings.class));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return settings;
    }
}