package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.fileprotection.FileProtectionActivity;
import org.telegram.messenger.partisan.fileprotection.FileProtectionUtils;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettings;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettingsFragment;

public class ChatSettingsFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanChatSettings);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new ToggleItem(this,
                        getString(R.string.ShowCallButton),
                        () -> SharedConfig.showCallButton,
                        newValue -> SharedConfig.toggleShowCallButton()),
                new DescriptionItem(this, getString(R.string.ShowCallButtonInfo)),
                new ToggleItem(this,
                        getString(R.string.ConfirmDangerousAction),
                        () -> SharedConfig.confirmDangerousActions,
                        newValue -> SharedConfig.toggleIsConfirmDangerousActions()),
                new DescriptionItem(this, getString(R.string.ConfirmDangerousActionInfo)),
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
                new ButtonItem(this,
                        getString(R.string.FileProtection),
                        () -> FileProtectionUtils.isFileProtectionEnabledForAnyAccount()
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new FileProtectionActivity())),
                new DescriptionItem(this, getString(R.string.FileProtectionInfo)),
                new ButtonItem(this,
                        getString(R.string.VoiceChange),
                        () -> VoiceChangeSettings.voiceChangeEnabled.get().orElse(false)
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new VoiceChangeSettingsFragment())),
                new DescriptionItem(this, getString(R.string.VoiceChangeDescription)),
        };
    }
}
