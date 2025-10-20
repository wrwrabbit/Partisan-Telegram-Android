package org.telegram.messenger.partisan;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.text.DecimalFormat;
import java.util.function.Consumer;

public class SeekBarCell extends FrameLayout {
    private double startValue = -1;
    private double endValue = -1;
    private double step = -1;
    private double currentValue;
    private Consumer<Double> delegate;
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.000");

    private final SeekBarView seekBar;
    private final TextPaint textPaint;
    private int lastWidth;

    public SeekBarCell(Context context) {
        super(context);

        setWillNotDraw(false);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(dp(16));

        seekBar = new SeekBarView(context);
        seekBar.setReportChanges(true);
        seekBar.setSeparatorsCount(10);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int index = Math.round(getStepsCount() * progress);
                onValueChanged(startValue + index * step);
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }

            @Override
            public CharSequence getContentDescription() {
                return String.valueOf(Math.round(startValue + (endValue - startValue) * seekBar.getProgress()));
            }

            @Override
            public int getStepsCount() {
                return SeekBarCell.this.getStepsCount();
            }
        });
        seekBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));
    }

    public void setValues(double startValue, double endValue, double step, double currentValue) {
        this.startValue = startValue;
        this.endValue = endValue;
        this.step = step;
        this.currentValue = currentValue;
        seekBar.setSeparatorsCount(getStepsCount() + 1);
    }

    private int getStepsCount() {
        return (int)Math.round((endValue - startValue) / step);
    }

    public void setDelegate(Consumer<Double> delegate) {
        this.delegate = delegate;
    }

    private void onValueChanged(double value) {
        if (value != currentValue) {
            currentValue = value;
            if (delegate != null) {
                delegate.accept(currentValue);
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        String valueStr = decimalFormat.format(currentValue);
        canvas.drawText(valueStr, getMeasuredWidth() - dp(39), dp(28), textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            double progress = (currentValue - startValue) / (endValue - startValue);
            seekBar.setProgress((float) progress);
            lastWidth = width;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        seekBar.invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        seekBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return super.performAccessibilityAction(action, arguments) || seekBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
    }
}
