package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.ButtonItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;
import org.telegram.ui.Components.AlertsCreator;

public class WhenLockedFragment extends PartisanBaseFragment {

    private AbstractViewItem onScreenLockActionItem;

    @Override
    protected String getTitle() {
        return getString(R.string.WhenLocked);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        onScreenLockActionItem = new ButtonItem(this,
                getString(R.string.OnScreenLockActionTitle),
                WhenLockedFragment::getOnScreenLockActionName,
                v -> AlertsCreator.showOnScreenLockActionsAlert(this, getParentActivity(),
                        () -> listAdapter.notifyItemChanged(onScreenLockActionItem.getPosition()), null))
                .withEllipsizeValue();
        return new AbstractViewItem[]{
                onScreenLockActionItem,
                new DescriptionItem(this, getString(R.string.OnScreenLockActionInfo)),
                new ToggleItem(this,
                        getString(R.string.IsClearAllDraftsOnScreenLock),
                        () -> SharedConfig.clearAllDraftsOnScreenLock,
                        newValue -> SharedConfig.toggleClearAllDraftsOnScreenLock()),
                new DescriptionItem(this, getString(R.string.IsClearAllDraftsOnScreenLockInfo)),
                new ToggleItem(this,
                        getString(R.string.ClearCacheOnLock),
                        () -> SharedConfig.clearCacheOnLock,
                        newValue -> {
                            SharedConfig.clearCacheOnLock = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.ClearCacheOnLockInfo)),
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
}
