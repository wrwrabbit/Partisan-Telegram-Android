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

import java.util.Arrays;
import java.util.function.Consumer;

public class SeekBarCell extends FrameLayout {
    private int startValue = -1;
    private int endValue = -1;
    private Object[] values;
    private int currentValue;
    private Consumer<Object> delegate;

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
                onValueChanged(Math.round(startValue + (endValue - startValue) * progress));
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
                return endValue - startValue;
            }
        });
        seekBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));
    }

    public void setValues(int startValue, int endValue, int currentValue) {
        this.startValue = startValue;
        this.endValue = endValue;
        this.currentValue = currentValue;
        this.values = null;
        seekBar.setSeparatorsCount(endValue - startValue + 1);
    }

    public void setValues(Object[] values, Object currentValue) {
        this.values = values;
        this.currentValue = Arrays.asList(values).indexOf(currentValue);
        this.startValue = 0;
        this.endValue = values.length - 1;
        seekBar.setSeparatorsCount(endValue - startValue + 1);
    }

    public void setDelegate(Consumer<Object> delegate) {
        this.delegate = delegate;
    }

    private void onValueChanged(int value) {
        currentValue = value;
        if (delegate != null) {
            delegate.accept(getCurrentValueObject());
        }
        invalidate();
    }

    private Object getCurrentValueObject() {
        if (values != null && currentValue < values.length) {
            return values[currentValue];
        } else {
            return currentValue;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        canvas.drawText(getCurrentValueObject().toString(), getMeasuredWidth() - dp(39), dp(28), textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            seekBar.setProgress((currentValue - startValue) / (float) (endValue - startValue));
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
