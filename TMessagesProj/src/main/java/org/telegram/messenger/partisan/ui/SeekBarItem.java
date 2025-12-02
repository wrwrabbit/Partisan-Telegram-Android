package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.SeekBarCell;
import org.telegram.messenger.partisan.settings.FloatSetting;
import org.telegram.messenger.partisan.settings.IntSetting;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SeekBarItem extends AbstractItem {
    private final Supplier<Float> getValue;
    private final Consumer<Float> setValue;
    private final double startValue;
    private final double endValue;
    private final double step;

    public SeekBarItem(BaseFragment fragment, FloatSetting setting, double startValue, double endValue, double step) {
        this(fragment, () -> setting.get().get(), setting::set, startValue, endValue, step);
    }

    public SeekBarItem(BaseFragment fragment, IntSetting setting, double startValue, double endValue, double step) {
        this(fragment, () -> (float)setting.get().get(), val -> setting.set((int)(float)val), startValue, endValue, step);
    }

    public SeekBarItem(BaseFragment fragment, Supplier<Float> getValue, Consumer<Float> setValue, double startValue, double endValue, double step) {
        super(fragment, ItemType.SEEK_BAR.ordinal());
        this.getValue = getValue;
        this.setValue = setValue;
        this.startValue = startValue;
        this.endValue = endValue;
        this.step = step;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new SeekBarCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        SeekBarCell seekBarCell = (SeekBarCell) holder.itemView;
        seekBarCell.setValues(startValue, endValue, step, getValue.get());
        seekBarCell.setDelegate(value -> setValue.accept((float)(double)value));
        seekBarCell.invalidate();
    }

    @Override
    public void onClick(View view) {}

    @Override
    public boolean isEnabledInternal() {
        return true;
    }
}
