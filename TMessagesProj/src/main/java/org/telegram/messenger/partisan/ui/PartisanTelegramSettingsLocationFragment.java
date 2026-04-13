package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettingsLocation;

public class PartisanTelegramSettingsLocationFragment extends PartisanBaseFragment {

    private AbstractItem settingsItem;
    private AbstractItem privacyItem;
    private AbstractItem disabledItem;

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanTelegramSettingsPosition);
    }

    @Override
    protected AbstractItem[] createItems() {
        settingsItem = new RadioButtonItem(this,
                getString(R.string.PartisanTelegramSettingsPositionMain),
                () -> PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == PartisanTelegramSettingsLocation.SETTINGS_ACTIVITY,
                () -> selectLocation(PartisanTelegramSettingsLocation.SETTINGS_ACTIVITY));
        privacyItem = new RadioButtonItem(this,
                getString(R.string.PartisanTelegramSettingsPositionPrivacy),
                () -> PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == PartisanTelegramSettingsLocation.PRIVACY_AND_SECURITY,
                () -> selectLocation(PartisanTelegramSettingsLocation.PRIVACY_AND_SECURITY));
        disabledItem = new RadioButtonItem(this,
                getString(R.string.PartisanTelegramSettingsPositionDisabled),
                () -> PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault() == PartisanTelegramSettingsLocation.DISABLED,
                () -> selectLocation(PartisanTelegramSettingsLocation.DISABLED));
        return new AbstractItem[]{
                settingsItem,
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionSettingsInfo)),
                privacyItem,
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionPrivacyInfo)),
                disabledItem,
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionDisabledInfo)),
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
        listAdapter.notifyItemChanged(disabledItem.getPosition());
    }
}
