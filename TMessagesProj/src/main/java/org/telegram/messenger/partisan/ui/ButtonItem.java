package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ButtonItem extends AbstractItem {
    private final String text;
    private final Consumer<View> onClick;
    private final Supplier<String> getValue;

    public ButtonItem(BaseFragment fragment, String text, Consumer<View> onClick) {
        super(fragment, ItemType.BUTTON.ordinal());
        this.text = text;
        this.getValue = null;
        this.onClick = onClick;
    }

    public ButtonItem(BaseFragment fragment, String text, Supplier<String> getValue, Consumer<View> onClick) {
        super(fragment, ItemType.BUTTON.ordinal());
        this.text = text;
        this.getValue = getValue;
        this.onClick = onClick;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new TextSettingsCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
        textCell.setCanDisable(true);
        if (getValue != null) {
            textCell.setTextAndValue(text, getValue.get(), true);
        } else {
            textCell.setText(text, true);
        }
    }

    @Override
    public void onClick(View view) {
        onClick.accept(view);
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
