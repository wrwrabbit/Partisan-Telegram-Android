package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.PartisanSlideChooseView;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ValuesSlideChooseItem extends AbstractItem {
    private final String[] options;
    private final Supplier<Integer> getValue;
    private final Consumer<Integer> setValue;

    public ValuesSlideChooseItem(BaseFragment fragment, String[] options, Supplier<Integer> getValue, Consumer<Integer> setValue) {
        super(fragment, ItemType.VALUES_SLIDER.ordinal());
        this.options = options;
        this.getValue = getValue;
        this.setValue = setValue;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new PartisanSlideChooseView(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        PartisanSlideChooseView slider = (PartisanSlideChooseView)holder.itemView;
        slider.setCallback(setValue::accept);
        slider.setOptions(getValue.get(), options);
    }

    @Override
    public void onClick(View view) {}

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
