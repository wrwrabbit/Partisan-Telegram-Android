package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.util.function.Supplier;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.ui.items.AbstractSourceItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.CollapseItem;
import org.telegram.messenger.partisan.ui.items.DelimiterItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.HeaderItem;
import org.telegram.messenger.partisan.ui.items.ItemsGenerator;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettings;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettingsFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.FakePasscodeActivity;
import org.telegram.ui.FakePasscodeRestoreActivity;
import org.telegram.ui.PartisanSettingsActivity;
import org.telegram.ui.PasscodeActivity;
import org.telegram.ui.SecurityIssuesFragment;

public class PTelegramSettingsFragment extends PartisanBaseFragment {

    private static final int MAX_VISIBLE_FAKE_PASSCODES = 3;
    private static final int MIN_FOLD_FAKE_PASSCODES = 5;
    private boolean showAllFakePasscodes = false;

    public PTelegramSettingsFragment() {
        super();
    }

    public static BaseFragment checkLockAndCreateActivity() {
        if (SharedConfig.protectPtelegramSettings && SharedConfig.passcodeEnabled()) {
            return new org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLockActivity(PTelegramSettingsFragment::new);
        }
        return new PTelegramSettingsFragment();
    }

    public static BaseFragment checkLockAndCreate(Supplier<BaseFragment> fragmentFactory) {
        if (SharedConfig.protectPtelegramSettings && SharedConfig.passcodeEnabled()) {
            org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLockActivity lockActivity = new org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLockActivity(fragmentFactory);
            return lockActivity;
        }
        return fragmentFactory.get();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanTelegramSettings);
    }

    @Override
    protected AbstractSourceItem[] createItems() {
        return new AbstractSourceItem[]{
                new HeaderItem(this, getString(R.string.FakePasscodes)),
                new ItemsGenerator(
                        () -> (!isNeedFoldFakePasscodes() || showAllFakePasscodes)
                                ? SharedConfig.fakePasscodes.size()
                                : MAX_VISIBLE_FAKE_PASSCODES,
                        passcodeIndex -> {
                            if (passcodeIndex >= SharedConfig.fakePasscodes.size()) {
                                return null;
                            }
                            FakePasscode passcode = SharedConfig.fakePasscodes.get(passcodeIndex);
                            return new ButtonItem(this, passcode.name, v ->
                                    presentFragment(new FakePasscodeActivity(
                                            FakePasscodeActivity.TYPE_FAKE_PASSCODE_SETTINGS, passcode, false)));
                        }
                ),
                new CollapseItem(this, getString(R.string.MoreAccounts), () -> {
                    showAllFakePasscodes = true;
                    listAdapter.updateRows();
                    listAdapter.notifyDataSetChanged();
                }).addCondition(() -> isNeedFoldFakePasscodes() && !showAllFakePasscodes),
                new ButtonItem(this, getString(R.string.AddFakePasscode), v ->
                        showNoMainPasscodeWarningIfNeeded(() -> {
                            FakePasscode newFakePasscode = FakePasscode.create();
                            presentFragment(new FakePasscodeActivity(
                                    FakePasscodeActivity.TYPE_SETUP_FAKE_PASSCODE, newFakePasscode, true));
                        }))
                        .withThemeKey(Theme.key_windowBackgroundWhiteBlueText4),
                new DelimiterItem(this),
                new ButtonItem(this, getString(R.string.FakePasscodeRestore), v ->
                        showNoMainPasscodeWarningIfNeeded(() ->
                                presentFragment(new FakePasscodeRestoreActivity())))
                        .withThemeKey(Theme.key_windowBackgroundWhiteBlueText4),
                new DescriptionItem(this, getString(R.string.FakePasscodeActionsInfo)),
                new ButtonItem(this, getString(R.string.BadPasscodeReaction), v ->
                        presentFragment(new org.telegram.messenger.partisan.ui.BadPasscodeReactionFragment())),
                new DescriptionItem(this, getString(R.string.BadPasscodeReactionInfo)),
                new ButtonItem(this, getString(R.string.VoiceChange),
                        () -> VoiceChangeSettings.voiceChangeEnabled.get().orElse(false)
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new VoiceChangeSettingsFragment())),
                new DescriptionItem(this, getString(R.string.VoiceChangeDescription)),
                new ButtonItem(this, getString(R.string.InterfaceTweaks),
                        InterfaceTweaksFragment::getEnabledSummary,
                        v -> presentFragment(new InterfaceTweaksFragment())),
                new DescriptionItem(this, getString(R.string.InterfaceTweaksInfo)),
                new ButtonItem(this, getString(R.string.SecurityIssuesTitle),
                        () -> String.valueOf(getUserConfig().getActiveSecurityIssues().size()),
                        v -> presentFragment(new SecurityIssuesFragment())),
                new DescriptionItem(this, getString(R.string.SecurityIssuesInfo)),
                new ButtonItem(this, getString(R.string.OtherSettings), v ->
                        presentFragment(new PartisanSettingsActivity(true))),
                new DescriptionItem(this, getString(R.string.PartisanSettingsInfo)),
                new HeaderItem(this, getString(R.string.PartisanTelegramSettings)),
                new ToggleItem(this, getString(R.string.ProtectPartisanSettings),
                        () -> SharedConfig.protectPtelegramSettings,
                        newValue -> {
                            SharedConfig.protectPtelegramSettings = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ProtectPartisanSettingsInfo)),
                new ButtonItem(this, getString(R.string.PartisanTelegramSettingsPosition),
                        () -> {
                            String[] positionOptions = {
                                    getString(R.string.Settings),
                                    getString(R.string.PrivacySettings)
                            };
                            return positionOptions[PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault().ordinal()];
                        },
                        v -> presentFragment(new PartisanTelegramSettingsLocationFragment()))
                        .withEllipsizeValue(),
                new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionInfo)),
        };
    }

    private static boolean isNeedFoldFakePasscodes() {
        return SharedConfig.fakePasscodes.size() >= MIN_FOLD_FAKE_PASSCODES;
    }

    private void showNoMainPasscodeWarningIfNeeded(Runnable action) {
        if (!SharedConfig.passcodeEnabled() && SharedConfig.showFakePasscodeNoMainPasscodeWarning) {
            showDialog(buildNoMainPasscodeWarning(action));
        } else {
            action.run();
        }
    }

    private AlertDialog buildNoMainPasscodeWarning(Runnable action) {
        CheckBox checkBox = new CheckBox(getParentActivity());
        checkBox.setText(LocaleController.getString(R.string.DoNotShowAgain));
        checkBox.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        layout.addView(checkBox);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.FakePasscodeNoMainPasscodeTitle));
        builder.setMessage(LocaleController.getString(R.string.FakePasscodeNoMainPasscodeMessage));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.CheckPhoneNumberYes), (dialog, which) -> {
            if (checkBox.isChecked()) {
                SharedConfig.showFakePasscodeNoMainPasscodeWarning = false;
                SharedConfig.saveConfig();
            }
            PasscodeActivity passcodeActivity = new PasscodeActivity(PasscodeActivity.TYPE_SETUP_CODE);
            passcodeActivity.returnToPreviousFragment = true;
            presentFragment(passcodeActivity);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.CheckPhoneNumberNo), (dialog, which) -> {
            if (checkBox.isChecked()) {
                SharedConfig.showFakePasscodeNoMainPasscodeWarning = false;
                SharedConfig.saveConfig();
            }
            action.run();
        });
        return builder.create();
    }
}
