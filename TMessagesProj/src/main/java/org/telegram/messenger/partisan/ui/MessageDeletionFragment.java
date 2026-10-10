package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.ToggleItem;

public class MessageDeletionFragment extends PartisanBaseFragment {

    @Override
    protected String getTitle() {
        return getString(R.string.MessageDeletion);
    }

    public static String getEnabledSummary() {
        return formatEnabledCount(
                SharedConfig.deleteMessagesForAllByDefault,
                SharedConfig.showDeleteMyMessages,
                SharedConfig.showDeleteAfterRead
        );
    }

    @Override
    protected AbstractViewItem[] createItems() {
        return new AbstractViewItem[]{
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
        };
    }
}
