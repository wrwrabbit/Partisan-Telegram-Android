package org.telegram.messenger.partisan.voicechange;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.partisan.ui.AbstractItem;
import org.telegram.messenger.partisan.ui.ItemType;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class RecordItem extends AbstractItem {
    private final Supplier<Boolean> isRecording;
    private final Consumer<View> onClick;

    public RecordItem(BaseFragment fragment, Supplier<Boolean> isRecording, Consumer<View> onClick) {
        super(fragment, ItemType.RECORD.ordinal());
        this.isRecording = isRecording;
        this.onClick = onClick;
    }

    public static View createView(Context context) {
        return AbstractItem.initializeView(new RecordTextCell(context));
    }

    @Override
    public void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position) {
        RecordTextCell recordCell = (RecordTextCell) holder.itemView;
        if (isRecording.get()) {
            recordCell.setTextAndIcon(getString(R.string.Stop), R.drawable.quantum_ic_stop_white_24, true);
            recordCell.setRecording(true);
        } else {
            recordCell.setTextAndIcon(getString(R.string.RecordVoiceChangeExample), R.drawable.input_mic, true);
            recordCell.setRecording(false);
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

    @Override
    protected void setEnabled(View view, boolean enabled) {
        RecordTextCell recordCell = (RecordTextCell) view;
        recordCell.setEnabled(isEnabled());
        recordCell.showEnabledAlpha(!isEnabled());
    }
}
