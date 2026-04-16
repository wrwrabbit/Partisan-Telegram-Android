package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class SavedChannelsSettingsFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.SavedChannels);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new ToggleItem(this, getString(R.string.Enable),
                        () -> SharedConfig.showSavedChannels,
                        value -> {
                            DangerousSettingSwitcher switcher = new DangerousSettingSwitcher();
                            switcher.fragment = this;
                            switcher.value = SharedConfig.showSavedChannels;
                            switcher.setValue = v -> {
                                SharedConfig.showSavedChannels = v;
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.savedChannelsButtonStateChanged);
                            };
                            switcher.isChanged = config -> {
                                List<String> savedChannels = Arrays.asList(config.defaultChannels.split(","));
                                if (savedChannels.size() != config.savedChannels.size() || !savedChannels.containsAll(config.savedChannels)) {
                                    return true;
                                }
                                return !savedChannels.equals(config.pinnedSavedChannels);
                            };
                            switcher.dangerousActionTitle = getString(R.string.ClearSavedChannelsTitle);
                            switcher.positiveButtonText = getString(R.string.ClearButton);
                            switcher.negativeButtonText = getString(R.string.NotClear);
                            switcher.neutralButtonText = getString(R.string.Cancel);
                            switcher.dangerousAction = accountInstance -> {
                                UserConfig config = accountInstance.getUserConfig();
                                List<String> savedChannels = Arrays.asList(config.defaultChannels.split(","));
                                config.savedChannels = new HashSet<>(savedChannels);
                                config.pinnedSavedChannels = new ArrayList<>(savedChannels);
                                config.saveConfig(false);
                            };
                            switcher.onSettingChanged = () -> listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
                            switcher.switchSetting();
                        }),
                new DescriptionItem(this, getString(R.string.SavedChannelsSettingInfo)),
                new ToggleItem(this, getString(R.string.SavedChannelsInChatList),
                        PartisanTelegramSettings.showInChatList::getOrDefault,
                        value -> {
                            PartisanTelegramSettings.showInChatList.set(value);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.savedChannelsButtonStateChanged);
                        })
                        .addEnabledCondition(() -> SharedConfig.showSavedChannels),
                new DescriptionItem(this, getString(R.string.SavedChannelsInChatListInfo)),
                new ToggleItem(this, getString(R.string.SavedChannelsInSettings),
                        PartisanTelegramSettings.showInSettings::getOrDefault,
                        value -> {
                            PartisanTelegramSettings.showInSettings.set(value);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.savedChannelsButtonStateChanged);
                        })
                        .addEnabledCondition(() -> SharedConfig.showSavedChannels),
                new DescriptionItem(this, getString(R.string.SavedChannelsInSettingsInfo)),
                new ToggleItem(this, getString(R.string.SavedChannelsAsTab),
                        PartisanTelegramSettings.showAsTab::getOrDefault,
                        value -> {
                            PartisanTelegramSettings.showAsTab.set(value);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.savedChannelsButtonStateChanged);
                        })
                        .addEnabledCondition(() -> SharedConfig.showSavedChannels),
                new DescriptionItem(this, getString(R.string.SavedChannelsAsTabInfo)),
        };
    }
}
