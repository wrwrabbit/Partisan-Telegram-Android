package org.telegram.messenger.partisan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;

import org.telegram.ui.Components.SlideChooseView;

public class PartisanSlideChooseView extends SlideChooseView {
    private boolean attached;
    private int componentsAlpha = 255;

    public PartisanSlideChooseView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected int getComponentsAlpha() {
        return componentsAlpha;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int alpha = enabled ? 255 : 128;
        if (attached) {
            final int previousAlpha = componentsAlpha;
            ValueAnimator animator = ValueAnimator.ofFloat(1.0f);
            animator.addUpdateListener(animation -> {
                int delta = alpha - previousAlpha;
                componentsAlpha = previousAlpha + (int)(animation.getAnimatedFraction() * delta);
                invalidate();
            });
            animator.start();
        } else {
            componentsAlpha = alpha;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }
}
