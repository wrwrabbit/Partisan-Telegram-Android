package org.telegram.messenger.partisan.ui.items;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.NotificationsCheckCell;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CombinedToggleItem extends AbstractViewItem {
    private final String text;
    private final Supplier<String> getValue;
    private final Supplier<Boolean> isChecked;
    private final Consumer<NotificationsCheckCell> onCheckboxClick;
    private final Consumer<NotificationsCheckCell> onRowClick;

    public CombinedToggleItem(BaseFragment fragment, String text, Supplier<String> getValue,
                              Supplier<Boolean> isChecked, Consumer<NotificationsCheckCell> onCheckboxClick,
                              Consumer<NotificationsCheckCell> onRowClick) {
        super(fragment, ItemType.COMBINED_TOGGLE.ordinal());
        this.text = text;
        this.getValue = getValue;
        this.isChecked = isChecked;
        this.onCheckboxClick = onCheckboxClick;
        this.onRowClick = onRowClick;
    }

    public static View createView(Context context) {
        return AbstractViewItem.initializeView(new NotificationsCheckCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        NotificationsCheckCell cell = (NotificationsCheckCell) holder.itemView;
        cell.setTextAndValueAndCheck(text, getValue.get(), isChecked.get(), drawDivider);
    }

    @Override
    public void onClickExtended(View view, float x, float y) {
        NotificationsCheckCell cell = (NotificationsCheckCell) view;
        if (cell.isCheckboxClicked(x)) {
            onCheckboxClick.accept(cell);
        } else {
            onRowClick.accept(cell);
        }
    }

    @Override
    public void onClick(View view) {
        // Not used — click is handled in onClickExtended
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
