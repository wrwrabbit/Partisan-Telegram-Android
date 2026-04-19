package org.telegram.messenger.partisan.settings;

public class PartisanTelegramSettings {
    public static final BooleanSetting showInChatList = new BooleanSetting("savedChannelsShowInChatList", true);
    public static final BooleanSetting showInSettings = new BooleanSetting("savedChannelsShowInSettings", true);
    public static final BooleanSetting showAsTab = new BooleanSetting("savedChannelsShowAsTab", false);

    public static final EnumSetting<PartisanTelegramSettingsLocation> partisanTelegramSettingsLocation =
            new EnumSetting<>("partisanTelegramSettingsLocation", PartisanTelegramSettingsLocation.SETTINGS_ACTIVITY, PartisanTelegramSettingsLocation.class);

    public static void loadSettings() {
        for (Setting<?> setting : SettingUtils.getAllSettings(PartisanTelegramSettings.class)) {
            setting.load();
        }
    }
}
