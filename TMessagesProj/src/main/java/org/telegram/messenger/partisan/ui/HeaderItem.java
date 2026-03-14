package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.HeaderCell;

public class HeaderItem extends AbstractItem {
    private final String text;

    public HeaderItem(BaseFragment fragment, String text) {
        super(fragment, ItemType.HEADER.ordinal());
        this.text = text;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new HeaderCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        HeaderCell headerCell = (HeaderCell) holder.itemView;
        headerCell.setHeight(46);
        headerCell.setText(text);
    }

    @Override
    public void onClick(View view) {}

    @Override
    public boolean isEnabledInternal() {
        return false;
    }
}
