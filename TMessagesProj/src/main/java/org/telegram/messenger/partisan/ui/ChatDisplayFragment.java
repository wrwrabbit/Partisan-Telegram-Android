package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.CombinedToggleItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.messenger.partisan.verification.VerificationStorage;
import org.telegram.messenger.partisan.verification.VerificationUtils;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.List;

public class ChatDisplayFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.ChatDisplay);
    }

    public static String getEnabledSummary() {
        return formatEnabledCount(
                SharedConfig.allowRenameChat,
                SharedConfig.allowDisableAvatar,
                SharedConfig.additionalVerifiedBadges,
                SharedConfig.cutForeignAgentsText,
                SharedConfig.showId
        );
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new ToggleItem(this,
                        getString(R.string.ChatRenaming),
                        () -> SharedConfig.allowRenameChat,
                        this::onAllowRenameChatChanged),
                new DescriptionItem(this, getString(R.string.ChatRenamingInfo)),
                new ToggleItem(this,
                        getString(R.string.AvatarDisabling),
                        () -> SharedConfig.allowDisableAvatar,
                        this::onAllowDisableAvatarChanged),
                new DescriptionItem(this, getString(R.string.AvatarDisablingInfo)),
                new CombinedToggleItem(this,
                        getString(R.string.AdditionalVerifiedSetting),
                        () -> {
                            List<VerificationStorage> storages = VerificationRepository.getInstance().getStorages();
                            return storages.size() == 1 ? storages.get(0).chatUsername : "";
                        },
                        () -> SharedConfig.additionalVerifiedBadges,
                        cell -> {
                            SharedConfig.toggleAdditionalVerifiedBadges();
                            cell.setChecked(SharedConfig.additionalVerifiedBadges);
                        },
                        cell -> VerificationUtils.showEditVerificationChannelUsernameDialog(this,
                                username -> cell.setTextAndValueAndCheck(getString(R.string.AdditionalVerifiedSetting), username, SharedConfig.additionalVerifiedBadges, false),
                                () -> {
                                    SharedConfig.toggleAdditionalVerifiedBadges();
                                    cell.setChecked(SharedConfig.additionalVerifiedBadges);
                                })),
                new DescriptionItem(this, getString(R.string.AdditionalVerifiedSettingInfo)),
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
                        getString(R.string.ShowId),
                        () -> SharedConfig.showId,
                        newValue -> {
                            SharedConfig.showId = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowIdInfo)),
        };
    }

    private void onAllowDisableAvatarChanged(boolean newValue) {
        if (!newValue && isAvatarDisabledInAnyAccount()) {
            showDangerousToggleDialog(
                    getString(R.string.ResetChangedAvatarsTitle),
                    () -> {
                        SharedConfig.allowDisableAvatar = false;
                        SharedConfig.saveConfig();
                        Utils.foreachActivatedAccountInstance(accountInstance -> {
                            for (UserConfig.ChatInfoOverride override : accountInstance.getUserConfig().chatInfoOverrides.values()) {
                                override.avatarEnabled = true;
                            }
                            accountInstance.getUserConfig().saveConfig(false);
                        });
                    },
                    () -> {
                        SharedConfig.allowDisableAvatar = false;
                        SharedConfig.saveConfig();
                    });
        } else {
            SharedConfig.allowDisableAvatar = newValue;
            SharedConfig.saveConfig();
        }
    }

    private void onAllowRenameChatChanged(boolean newValue) {
        if (!newValue && isChatRenamedInAnyAccount()) {
            showDangerousToggleDialog(
                    getString(R.string.ResetChangedTitlesTitle),
                    () -> {
                        SharedConfig.allowRenameChat = false;
                        SharedConfig.saveConfig();
                        Utils.foreachActivatedAccountInstance(accountInstance -> {
                            for (UserConfig.ChatInfoOverride override : accountInstance.getUserConfig().chatInfoOverrides.values()) {
                                override.title = null;
                            }
                            accountInstance.getUserConfig().saveConfig(false);
                        });
                    },
                    () -> {
                        SharedConfig.allowRenameChat = false;
                        SharedConfig.saveConfig();
                    });
        } else {
            SharedConfig.allowRenameChat = newValue;
            SharedConfig.saveConfig();
        }
    }

    private void showDangerousToggleDialog(String message, Runnable onReset, Runnable onNoReset) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AppName));
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.Reset), (dialog, which) -> {
            onReset.run();
            listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
        });
        builder.setNegativeButton(getString(R.string.NotReset), (dialog, which) -> {
            onNoReset.run();
            listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
        });
        builder.setNeutralButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private static boolean isAvatarDisabledInAnyAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() &&
                    config.chatInfoOverrides.values().stream().anyMatch(override -> !override.avatarEnabled)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChatRenamedInAnyAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() &&
                    config.chatInfoOverrides.values().stream().anyMatch(override -> override.title != null)) {
                return true;
            }
        }
        return false;
    }
}
