package org.telegram.messenger.partisan.ui.items;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CollapseTextCell;

public class CollapseItem extends AbstractViewItem {
    private final String text;
    private final Runnable onClick;

    public CollapseItem(BaseFragment fragment, String text, Runnable onClick) {
        super(fragment, ItemType.COLLAPSE.ordinal());
        this.text = text;
        this.onClick = onClick;
    }

    public static View createView(Context context) {
        CollapseTextCell cell = new CollapseTextCell(context, null);
        return AbstractViewItem.initializeView(cell);
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        CollapseTextCell collapseCell = (CollapseTextCell) holder.itemView;
        collapseCell.set(text, true);
        collapseCell.setColor(Theme.key_windowBackgroundWhiteBlueText4);
    }

    @Override
    public void onClick(View view) {
        onClick.run();
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
