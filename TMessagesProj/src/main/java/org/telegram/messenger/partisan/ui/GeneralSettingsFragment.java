package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.appmigration.AppMigrationActivity;
import org.telegram.messenger.partisan.appmigration.AppMigrator;
import org.telegram.messenger.partisan.appmigration.AppMigratorPreferences;
import org.telegram.messenger.partisan.fileprotection.FileProtectionActivity;
import org.telegram.messenger.partisan.fileprotection.FileProtectionUtils;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.SecurityIssuesFragment;

public class GeneralSettingsFragment extends PartisanBaseFragment {

    private AbstractViewItem onScreenLockActionItem;

    @Override
    protected String getTitle() {
        return getString(R.string.PartisanGeneralSettings);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                onScreenLockActionItem = new ButtonItem(this,
                        getString(R.string.OnScreenLockActionTitle),
                        GeneralSettingsFragment::getOnScreenLockActionName,
                        v -> AlertsCreator.showOnScreenLockActionsAlert(this, getParentActivity(),
                                () -> listAdapter.notifyItemChanged(onScreenLockActionItem.getPosition()), null))
                        .withEllipsizeValue(),
                new DescriptionItem(this, getString(R.string.OnScreenLockActionInfo)),
                new ToggleItem(this,
                        getString(R.string.ClearCacheOnLock),
                        () -> SharedConfig.clearCacheOnLock,
                        newValue -> {
                            SharedConfig.clearCacheOnLock = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ClearCacheOnLockInfo)),
                new ToggleItem(this,
                        getString(R.string.IsClearAllDraftsOnScreenLock),
                        () -> SharedConfig.clearAllDraftsOnScreenLock,
                        newValue -> SharedConfig.toggleClearAllDraftsOnScreenLock()),
                new DescriptionItem(this, getString(R.string.IsClearAllDraftsOnScreenLockInfo)),
                new ToggleItem(this,
                        getString(R.string.MarketIcons),
                        () -> SharedConfig.marketIcons,
                        newValue -> LauncherIconController.toggleMarketIcons())
                        .addCondition(ApplicationLoader::isRealBuildStandaloneBuild),
                new DescriptionItem(this, getString(R.string.MarketIconsInfo))
                        .addCondition(ApplicationLoader::isRealBuildStandaloneBuild),
                new ButtonItem(this,
                        getString(R.string.FileProtection),
                        () -> FileProtectionUtils.fileProtectionEnabledForAnyAccount()
                                ? getString(R.string.PasswordOn)
                                : getString(R.string.PasswordOff),
                        v -> presentFragment(new FileProtectionActivity())),
                new DescriptionItem(this, getString(R.string.FileProtectionInfo)),
                new ButtonItem(this,
                        getString(R.string.SecurityIssuesTitle),
                        () -> String.valueOf(getUserConfig().getActiveSecurityIssues().size()),
                        v -> presentFragment(new SecurityIssuesFragment())),
                new DescriptionItem(this, getString(R.string.SecurityIssuesInfo)),
                new ToggleItem(this,
                        getString(R.string.ShowVersion),
                        () -> SharedConfig.showVersion,
                        newValue -> {
                            SharedConfig.showVersion = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ShowVersionInfo)),
                new ToggleItem(this,
                        getString(R.string.AllowScreenshots),
                        () -> SharedConfig.allowScreenCapture,
                        this::setAllowScreenCapture)
                        .addCondition(SharedConfig::passcodeEnabled),
                new DescriptionItem(this, getString(R.string.ScreenCaptureInfo))
                        .addCondition(SharedConfig::passcodeEnabled),
                new ButtonItem(this,
                        getString(R.string.TransferDataToAnotherPtgButton),
                        v -> presentFragment(new AppMigrationActivity()))
                        .addCondition(GeneralSettingsFragment::canTransferDataToOtherPtg),
                new DescriptionItem(this, getString(R.string.TransferDataToOtherPtgInfo))
                        .addCondition(GeneralSettingsFragment::canTransferDataToOtherPtg),
        };
    }

    private static String getOnScreenLockActionName() {
        switch (SharedConfig.onScreenLockAction) {
            case 1:
                return getString(R.string.OnScreenLockActionHide);
            case 2:
                return getString(R.string.OnScreenLockActionClose);
            case 0:
            default:
                return getString(R.string.OnScreenLockActionNothing);
        }
    }

    private void setAllowScreenCapture(boolean allow) {
        SharedConfig.allowScreenCapture = allow;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode, false);
        if (!allow) {
            AlertsCreator.showSimpleAlert(this, getString(R.string.ScreenCaptureAlert));
        }
    }

    private static boolean canTransferDataToOtherPtg() {
        return AppMigrator.isNewerPtgInstalled(ApplicationLoader.applicationContext, false)
                || AppMigratorPreferences.isMigrationToMaskedPtg();
    }
}
