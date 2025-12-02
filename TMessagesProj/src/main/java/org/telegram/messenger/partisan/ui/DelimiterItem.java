package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;

public class DelimiterItem extends AbstractItem {
    public DelimiterItem(BaseFragment fragment) {
        super(fragment, ItemType.DELIMITER.ordinal());
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new ShadowSectionCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        holder.itemView.setBackground(Theme.getThemedDrawable(fragment.getContext(), R.drawable.greydivider, fragment.getThemedColor(Theme.key_windowBackgroundGrayShadow)));
    }

    @Override
    public void onClick(View view) {}

    @Override
    public boolean isEnabledInternal() {
        return false;
    }
}
