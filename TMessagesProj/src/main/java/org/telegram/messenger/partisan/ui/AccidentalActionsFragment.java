package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;

public class AccidentalActionsFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.AccidentalActions);
    }

    public static String getEnabledSummary() {
        return formatEnabledCount(
                SharedConfig.confirmDangerousActions,
                SharedConfig.allowReactions,
                SharedConfig.showCallButton
        );
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
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
        };
    }
}
