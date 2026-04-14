package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.widget.CheckBox;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.ui.AbstractItem;
import org.telegram.messenger.partisan.ui.ButtonItem;
import org.telegram.messenger.partisan.ui.CollapseItem;
import org.telegram.messenger.partisan.ui.DelimiterItem;
import org.telegram.messenger.partisan.ui.DescriptionItem;
import org.telegram.messenger.partisan.ui.HeaderItem;
import org.telegram.messenger.partisan.ui.InterfaceTweaksFragment;
import org.telegram.messenger.partisan.ui.PartisanBaseFragment;
import org.telegram.messenger.partisan.ui.PartisanListAdapter;
import org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLocationFragment;
import org.telegram.messenger.partisan.ui.ToggleItem;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettings;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettingsFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class PTelegramSettingsFragment extends PartisanBaseFragment {

    private static final int MAX_VISIBLE_FAKE_PASSCODES = 3;
    private static final int MIN_FOLD_FAKE_PASSCODES = 5;
    private boolean showAllFakePasscodes = false;

    public PTelegramSettingsFragment() {
        super();
    }

    public static BaseFragment checkLockAndCreateActivity() {
        if (SharedConfig.protectPtelegramSettings && SharedConfig.passcodeEnabled()) {
            return new org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLockActivity();
        }
        return new PTelegramSettingsFragment();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanTelegramSettings);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshItems();
    }

    private void refreshItems() {
        if (listAdapter != null && listView != null) {
            listAdapter = new PartisanListAdapter(createItems());
            listAdapter.setContext(getContext());
            listAdapter.updateRows();
            listView.setAdapter(listAdapter);
        }
    }

    @Override
    protected AbstractItem[] createItems() {
        List<AbstractItem> items = new ArrayList<>();

        items.add(new HeaderItem(this, getString(R.string.FakePasscodes)));
        boolean needFoldFakePasscodes = SharedConfig.fakePasscodes.size() >= MIN_FOLD_FAKE_PASSCODES;
        int visibleCount = (!needFoldFakePasscodes || showAllFakePasscodes)
                ? SharedConfig.fakePasscodes.size()
                : MAX_VISIBLE_FAKE_PASSCODES;
        for (int i = 0; i < visibleCount; i++) {
            FakePasscode passcode = SharedConfig.fakePasscodes.get(i);
            items.add(new ButtonItem(this, passcode.name, v ->
                    presentFragment(new FakePasscodeActivity(
                            FakePasscodeActivity.TYPE_FAKE_PASSCODE_SETTINGS, passcode, false))));
        }
        if (needFoldFakePasscodes && !showAllFakePasscodes) {
            items.add(new CollapseItem(this, getString(R.string.MoreAccounts), () -> {
                showAllFakePasscodes = true;
                refreshItems();
            }));
        }
        items.add(new ButtonItem(this, getString(R.string.AddFakePasscode), v ->
                showNoMainPasscodeWarningIfNeeded(() -> {
                    FakePasscode newFakePasscode = FakePasscode.create();
                    presentFragment(new FakePasscodeActivity(
                            FakePasscodeActivity.TYPE_SETUP_FAKE_PASSCODE, newFakePasscode, true));
                }))
                .withThemeKey(Theme.key_windowBackgroundWhiteBlueText4));
        items.add(new DelimiterItem(this));
        items.add(new ButtonItem(this, getString(R.string.FakePasscodeRestore), v ->
                showNoMainPasscodeWarningIfNeeded(() ->
                        presentFragment(new FakePasscodeRestoreActivity())))
                .withThemeKey(Theme.key_windowBackgroundWhiteBlueText4));
        items.add(new DescriptionItem(this, getString(R.string.FakePasscodeActionsInfo)));

        items.add(new ButtonItem(this, getString(R.string.BadPasscodeReaction), v ->
                presentFragment(new org.telegram.messenger.partisan.ui.BadPasscodeReactionFragment())));
        items.add(new DescriptionItem(this, getString(R.string.BadPasscodeReactionInfo)));
        items.add(new ButtonItem(this, getString(R.string.VoiceChange),
                () -> VoiceChangeSettings.voiceChangeEnabled.get().orElse(false)
                        ? getString(R.string.PasswordOn)
                        : getString(R.string.PasswordOff),
                v -> presentFragment(new VoiceChangeSettingsFragment())));
        items.add(new DescriptionItem(this, getString(R.string.VoiceChangeDescription)));
        items.add(new ButtonItem(this, getString(R.string.InterfaceTweaks),
                InterfaceTweaksFragment::getEnabledSummary,
                v -> presentFragment(new InterfaceTweaksFragment())));
        items.add(new DescriptionItem(this, getString(R.string.InterfaceTweaksInfo)));
        items.add(new ButtonItem(this, getString(R.string.SecurityIssuesTitle),
                () -> String.valueOf(getUserConfig().getActiveSecurityIssues().size()),
                v -> presentFragment(new SecurityIssuesFragment())));
        items.add(new DescriptionItem(this, getString(R.string.SecurityIssuesInfo)));
        items.add(new ButtonItem(this, getString(R.string.OtherSettings), v ->
                presentFragment(new PartisanSettingsActivity(true))));
        items.add(new DescriptionItem(this, getString(R.string.PartisanSettingsInfo)));

        items.add(new HeaderItem(this, getString(R.string.PartisanTelegramSettings)));
        items.add(new ToggleItem(this, getString(R.string.ProtectPartisanSettings),
                () -> SharedConfig.protectPtelegramSettings,
                newValue -> {
                    SharedConfig.protectPtelegramSettings = newValue;
                    SharedConfig.saveConfig();
                }));
        items.add(new DescriptionItem(this, getString(R.string.ProtectPartisanSettingsInfo)));
        items.add(new ButtonItem(this, getString(R.string.PartisanTelegramSettingsPosition),
                () -> {
                    String[] positionOptions = {
                        getString(R.string.Settings),
                        getString(R.string.PrivacySettings),
                        getString(R.string.PartisanTelegramSettingsPositionDisabled)
                    };
                    return positionOptions[PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault().ordinal()];
                },
                v -> presentFragment(new PartisanTelegramSettingsLocationFragment()))
                .withEllipsizeValue());
        items.add(new DescriptionItem(this, getString(R.string.PartisanTelegramSettingsPositionInfo)));

        return items.toArray(new AbstractItem[0]);
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
