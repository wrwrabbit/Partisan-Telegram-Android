package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.Utils;

public class InterfaceTweaksFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.InterfaceTweaks);
    }

    public static String getEnabledSummary() {
        boolean[] toggleValues = {
            SharedConfig.showVersion,
            SharedConfig.showId,
            SharedConfig.showCallButton,
            SharedConfig.allowReactions,
            SharedConfig.cutForeignAgentsText,
            SharedConfig.deleteMessagesForAllByDefault,
            SharedConfig.confirmDangerousActions,
        };
        int enabled = 0;
        for (boolean value : toggleValues) {
            if (value) {
                enabled++;
            }
        }
        return enabled + "/" + toggleValues.length;
    }

    @Override
    protected AbstractItem[] createItems() {
        return new AbstractItem[]{
                new ToggleItem(this,
                        getString(R.string.ShowVersion),
                        () -> SharedConfig.showVersion,
                        newValue -> {
                            SharedConfig.showVersion = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowVersionInfo)),
                new ToggleItem(this,
                        getString(R.string.ShowId),
                        () -> SharedConfig.showId,
                        newValue -> {
                            SharedConfig.showId = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowIdInfo)),
                new ToggleItem(this,
                        getString(R.string.ShowCallButton),
                        () -> SharedConfig.showCallButton,
                        newValue -> SharedConfig.toggleShowCallButton()),
                new DescriptionItem(this, getString(R.string.ShowCallButtonInfo)),
                new ButtonItem(this,
                        getString(R.string.SavedChannelsSetting),
                        () -> SharedConfig.showSavedChannels
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new SavedChannelsSettingsFragment())),
                new DescriptionItem(this, getString(R.string.SavedChannelsSettingInfo)),
                new ToggleItem(this,
                        getString(R.string.ReactToMessages),
                        () -> SharedConfig.allowReactions,
                        newValue -> {
                            SharedConfig.allowReactions = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ReactToMessagesInfo)),
                new ToggleItem(this,
                        getString(R.string.CutForeignAgentsText),
                        () -> SharedConfig.cutForeignAgentsText,
                        newValue -> {
                            SharedConfig.cutForeignAgentsText = newValue;
                            SharedConfig.saveConfig();
                            Utils.updateMessagesPreview();
                        }),
                new DescriptionItem(this, getString(R.string.CutForeignAgentsTextInfo)),
                new ToggleItem(this,
                        getString(R.string.IsDeleteMessagesForAllByDefault),
                        () -> SharedConfig.deleteMessagesForAllByDefault,
                        newValue -> SharedConfig.toggleIsDeleteMsgForAll()),
                new DescriptionItem(this, getString(R.string.IsDeleteMessagesForAllByDefaultInfo)),
                new ToggleItem(this,
                        getString(R.string.ConfirmDangerousAction),
                        () -> SharedConfig.confirmDangerousActions,
                        newValue -> SharedConfig.toggleIsConfirmDangerousActions()),
                new DescriptionItem(this, getString(R.string.ConfirmDangerousActionInfo)),
        };
    }
}
