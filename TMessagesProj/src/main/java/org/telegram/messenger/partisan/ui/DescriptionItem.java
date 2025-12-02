package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

public class DescriptionItem extends AbstractItem {
    private final String text;

    public DescriptionItem(BaseFragment fragment, String text) {
        super(fragment, ItemType.DESCRIPTION.ordinal());
        this.text = text;
    }

    public static View createView(Context context) {
        View view = new TextInfoPrivacyCell(context);
        view.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        return view;
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        ((TextInfoPrivacyCell) holder.itemView).setText(text);
    }

    @Override
    public void onClick(View view) {}

    @Override
    public boolean isEnabledInternal() {
        return false;
    }
}
