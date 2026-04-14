package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.messenger.partisan.verification.VerificationStorage;
import org.telegram.messenger.partisan.verification.VerificationUpdatesChecker;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.List;

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
            SharedConfig.allowDisableAvatar,
            SharedConfig.allowRenameChat,
            SharedConfig.showDeleteMyMessages,
            SharedConfig.showDeleteAfterRead,
            SharedConfig.additionalVerifiedBadges,
        };
        int enabled = 0;
        for (boolean toggleValue : toggleValues) {
            if (toggleValue) {
                enabled++;
            }
        }
        return enabled + "/" + toggleValues.length;
    }

    @Override
    protected AbstractItem[] createItems() {
        return new AbstractItem[]{
                new ButtonItem(this,
                        getString(R.string.SavedChannelsSetting),
                        () -> SharedConfig.showSavedChannels
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new SavedChannelsSettingsFragment())),
                new DescriptionItem(this, getString(R.string.SavedChannelsSettingInfo)),
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
                        cell -> {
                            List<VerificationStorage> storages = VerificationRepository.getInstance().getStorages();
                            if (storages.size() == 1) {
                                VerificationStorage storage = storages.get(0);
                                DialogTemplate template = new DialogTemplate();
                                template.type = DialogType.ONLY_SAVE;
                                template.title = getString(R.string.VerificationChannelUsername);
                                template.addEditTemplate(storage.chatUsername, getString(R.string.VerificationChannelUsername), true);
                                template.positiveListener = views -> {
                                    String username = ((EditTextCaption) views.get(0)).getText().toString();
                                    username = Utils.removeUsernamePrefixed(username);
                                    VerificationRepository.getInstance().deleteStorage(storage.chatId);
                                    VerificationRepository.getInstance().addStorage("Custom", username, -1);
                                    VerificationUpdatesChecker.checkUpdate(currentAccount, true);
                                    cell.setTextAndValueAndCheck(getString(R.string.AdditionalVerifiedSetting), username, SharedConfig.additionalVerifiedBadges, false);
                                };
                                template.negativeListener = (dlg, whichButton) -> {
                                    SharedConfig.toggleAdditionalVerifiedBadges();
                                    cell.setChecked(SharedConfig.additionalVerifiedBadges);
                                };
                                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                                showDialog(dialog);
                            }
                        }),
                new DescriptionItem(this, getString(R.string.AdditionalVerifiedSettingInfo)),
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
                        getString(R.string.ShowCallButton),
                        () -> SharedConfig.showCallButton,
                        newValue -> SharedConfig.toggleShowCallButton()),
                new DescriptionItem(this, getString(R.string.ShowCallButtonInfo)),
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
                        getString(R.string.DeletingMyMessages),
                        () -> SharedConfig.showDeleteMyMessages,
                        newValue -> {
                            SharedConfig.showDeleteMyMessages = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.DeletingMyMessagesInfo)),
                new ToggleItem(this,
                        getString(R.string.DeletingAfterRead),
                        () -> SharedConfig.showDeleteAfterRead,
                        newValue -> {
                            SharedConfig.showDeleteAfterRead = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.DeletingAfterReadInfo)),
                new ToggleItem(this,
                        getString(R.string.AvatarDisabling),
                        () -> SharedConfig.allowDisableAvatar,
                        this::onAllowDisableAvatarChanged),
                new DescriptionItem(this, getString(R.string.AvatarDisablingInfo)),
                new ToggleItem(this,
                        getString(R.string.ChatRenaming),
                        () -> SharedConfig.allowRenameChat,
                        this::onAllowRenameChatChanged),
                new DescriptionItem(this, getString(R.string.ChatRenamingInfo)),
                new ToggleItem(this,
                        getString(R.string.ShowId),
                        () -> SharedConfig.showId,
                        newValue -> {
                            SharedConfig.showId = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowIdInfo)),
                new ToggleItem(this,
                        getString(R.string.ShowVersion),
                        () -> SharedConfig.showVersion,
                        newValue -> {
                            SharedConfig.showVersion = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowVersionInfo)),
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
