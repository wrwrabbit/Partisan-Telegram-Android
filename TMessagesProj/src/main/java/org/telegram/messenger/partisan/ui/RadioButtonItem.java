package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.SimpleRadioButtonCell;

import java.util.function.Supplier;

public class RadioButtonItem extends AbstractItem {
    private final String text;
    private final Supplier<Boolean> getValue;
    private final Runnable setValue;

    public RadioButtonItem(BaseFragment fragment, String text, Supplier<Boolean> getValue, Runnable setValue) {
        super(fragment, ItemType.RADIO_BUTTON.ordinal());
        this.text = text;
        this.getValue = getValue;
        this.setValue = setValue;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new SimpleRadioButtonCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        ((SimpleRadioButtonCell) holder.itemView).setTextAndValue(text, true, getValue.get());
    }

    @Override
    public void onClick(View view) {
        setValue.run();
        ((SimpleRadioButtonCell) view).setChecked(getValue.get(), true);
    }

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
