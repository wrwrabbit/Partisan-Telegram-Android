package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCell;

import java.util.function.Consumer;

public class ButtonWithIconItem extends AbstractItem {
    private final String text;
    private final Integer iconResId;
    private final Consumer<View> onClick;

    public ButtonWithIconItem(BaseFragment fragment, String text, Integer iconResId, Consumer<View> onClick) {
        super(fragment, ItemType.BUTTON_WITH_ICON.ordinal());
        this.text = text;
        this.iconResId = iconResId;
        this.onClick = onClick;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new TextCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        TextCell textCell = (TextCell) holder.itemView;
        textCell.setTextAndIcon(text, iconResId, true);
    }

    @Override
    public void onClick(View view) {
        onClick.accept(view);
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }

    @Override
    public void setEnabled(View view, boolean enabled) {
        TextCell textCell = (TextCell) view;
        textCell.setEnabled(isEnabled());
        textCell.showEnabledAlpha(!isEnabled());
    }
}
