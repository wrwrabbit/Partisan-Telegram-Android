package org.telegram.messenger.partisan.ui;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.function.Supplier;

public class ReadOnlyDataItem extends AbstractItem {
    private final String text;
    private final Supplier<String> getValue;
    private Runnable onClick;

    public ReadOnlyDataItem(BaseFragment fragment, String text, Supplier<String> getValue) {
        super(fragment, ItemType.BUTTON.ordinal());
        this.text = text;
        this.getValue = getValue;
    }

    public ReadOnlyDataItem setOnClickListener(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        ((TextSettingsCell) holder.itemView).setTextAndValue(text, getValue.get(), true);
    }

    @Override
    public void onClick(View view) {
        if (onClick != null) {
            onClick.run();
        }
    }

    @Override
    public boolean isEnabledInternal() {
        return onClick != null;
    }
}
