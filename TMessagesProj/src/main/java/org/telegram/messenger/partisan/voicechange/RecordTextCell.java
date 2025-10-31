package org.telegram.messenger.partisan.voicechange;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;

class RecordTextCell extends TextCell {
    private final RecordDot recordDot;

    public RecordTextCell(Context context) {
        super(context);
        recordDot = new RecordDot(context);
        addView(recordDot, LayoutHelper.createFrame(37, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int height = bottom - top;
        int width = right - left;

        int viewTop = (height - dp(20)) / 2;
        int viewLeft = LocaleController.isRTL ? dp(leftPadding) : width - recordDot.getMeasuredWidth() - dp(leftPadding);

        recordDot.layout(viewLeft, viewTop, viewLeft + recordDot.getMeasuredWidth(), viewTop + valueTextView.getMeasuredHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (recordDot != null) {
            recordDot.measure(MeasureSpec.makeMeasureSpec(dp(37), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.EXACTLY));
        }
    }

    public void setRecording(boolean recording) {
        recordDot.setRecording(recording);
    }
}
