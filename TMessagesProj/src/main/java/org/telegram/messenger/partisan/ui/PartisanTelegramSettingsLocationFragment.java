package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettingsLocation;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.RadioButtonItem;

public class PartisanTelegramSettingsLocationFragment extends PartisanBaseFragment {

    private AbstractViewItem settingsItem;
    private AbstractViewItem privacyItem;

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanTelegramSettingsPosition);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        settingsItem = new RadioButtonItem(this,
                getString(R.string.PartisanTelegramSettingsPositionMain),
                () -> PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == PartisanTelegramSettingsLocation.SETTINGS_ACTIVITY,
                () -> selectLocation(PartisanTelegramSettingsLocation.SETTINGS_ACTIVITY));
        privacyItem = new RadioButtonItem(this,
                getString(R.string.PartisanTelegramSettingsPositionPrivacy),
                () -> PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == PartisanTelegramSettingsLocation.PRIVACY_AND_SECURITY,
                () -> selectLocation(PartisanTelegramSettingsLocation.PRIVACY_AND_SECURITY));
        return new AbstractViewItem[]{
                settingsItem,
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionSettingsInfo)),
                privacyItem,
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionPrivacyInfo)),
        };
    }

    private void selectLocation(PartisanTelegramSettingsLocation location) {
        if (PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == location) {
            return;
        }
        PartisanTelegramSettings.partisanTelegramSettingsLocation.set(location);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.partisanTelegramSettingsButtonStateChanged);
        listAdapter.notifyItemChanged(settingsItem.getPosition());
        listAdapter.notifyItemChanged(privacyItem.getPosition());
    }
}
