package org.telegram.messenger.partisan.ui;

import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.messenger.partisan.settings.Setting;
import org.telegram.messenger.partisan.settings.SettingUtils;

public class SavedChannelsSettings {
    public static final BooleanSetting showInChatList = new BooleanSetting("savedChannelsShowInChatList", true);
    public static final BooleanSetting showInSettings = new BooleanSetting("savedChannelsShowInSettings", true);
    public static final BooleanSetting showAsTab = new BooleanSetting("savedChannelsShowAsTab", false);

    public static void loadSettings() {
        for (Setting<?> setting : SettingUtils.getAllSettings(SavedChannelsSettings.class)) {
            setting.load();
        }
    }
}
