package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.screenlock.OnScreenLockActions;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.HeaderItem;
import org.telegram.messenger.partisan.ui.items.RadioButtonItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;

public class OnScreenLockActionFragment extends PartisanBaseFragment {

    private AbstractViewItem nothingItem;
    private AbstractViewItem hideItem;
    private AbstractViewItem closeItem;

    @Override
    protected String getTitle() {
        return getString(R.string.OnScreenLockActionTitle);
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
                new HeaderItem(this, getString(R.string.OnScreenLockAppBehavior)),
                nothingItem = createActionItem(OnScreenLockActions.ACTION_NOTHING, R.string.OnScreenLockActionNothing),
                new DescriptionItem(this, getString(R.string.OnScreenLockActionNothingInfo)),
                hideItem = createActionItem(OnScreenLockActions.ACTION_HIDE, R.string.OnScreenLockActionHide),
                new DescriptionItem(this, getString(R.string.OnScreenLockActionHideInfo)),
                closeItem = createActionItem(OnScreenLockActions.ACTION_CLOSE, R.string.OnScreenLockActionClose),
                new DescriptionItem(this, getString(R.string.OnScreenLockActionCloseInfo)),
                new HeaderItem(this, getString(R.string.OnScreenLockDataCleanup)),
                new ToggleItem(this,
                        getString(R.string.ClearCache),
                        () -> SharedConfig.onScreenLockActionClearCache,
                        newValue -> {
                            SharedConfig.onScreenLockActionClearCache = newValue;
                            SharedConfig.saveConfig();
                        }),
                new DescriptionItem(this, getString(R.string.OnScreenLockClearCacheInfo)),
                new ToggleItem(this,
                        getString(R.string.OnScreenLockDeleteAllDrafts),
                        () -> SharedConfig.clearAllDraftsOnScreenLock,
                        newValue -> SharedConfig.toggleClearAllDraftsOnScreenLock()),
                new DescriptionItem(this, getString(R.string.IsClearAllDraftsOnScreenLockInfo)),
        };
    }

    private AbstractViewItem createActionItem(int action, int textResId) {
        return new RadioButtonItem(this,
                getString(textResId),
                () -> SharedConfig.onScreenLockAction == action,
                () -> selectAction(action));
    }

    private void selectAction(int action) {
        if (SharedConfig.onScreenLockAction == action) {
            return;
        }
        SharedConfig.onScreenLockAction = action;
        SharedConfig.saveConfig();
        listAdapter.notifyItemChanged(nothingItem.getPosition());
        listAdapter.notifyItemChanged(hideItem.getPosition());
        listAdapter.notifyItemChanged(closeItem.getPosition());
    }

    public static String getSelectedActionName() {
        switch (SharedConfig.onScreenLockAction) {
            case OnScreenLockActions.ACTION_HIDE:
                return getString(R.string.OnScreenLockActionHide);
            case OnScreenLockActions.ACTION_CLOSE:
                return getString(R.string.OnScreenLockActionClose);
            case OnScreenLockActions.ACTION_NOTHING:
            default:
                return getString(R.string.OnScreenLockActionNothing);
        }
    }
}
