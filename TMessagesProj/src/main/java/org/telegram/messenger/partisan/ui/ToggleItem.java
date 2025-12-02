package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCheckCell;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ToggleItem extends AbstractItem {
    private final String text;
    private final Supplier<Boolean> getValue;
    private final Consumer<Boolean> setValue;

    public ToggleItem(BaseFragment fragment, String text, BooleanSetting booleanSetting) {
        this(fragment, text, booleanSetting::getOrDefault, booleanSetting::set);
    }

    public ToggleItem(BaseFragment fragment, String text, Supplier<Boolean> getValue, Consumer<Boolean> setValue) {
        super(fragment, ItemType.TOGGLE.ordinal());
        this.text = text;
        this.getValue = getValue;
        this.setValue = setValue;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new TextCheckCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        TextCheckCell textCell = (TextCheckCell) holder.itemView;
        textCell.setEnabled(isEnabled(), null);
        textCell.setTextAndCheck(text, getValue.get(), true);
    }

    @Override
    public void onClick(View view) {
        setValue.accept(!getValue.get());
        ((TextCheckCell) view).setChecked(getValue.get());
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
