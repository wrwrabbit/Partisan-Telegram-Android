package org.telegram.messenger.partisan;

import android.content.Context;
import android.view.MotionEvent;

import org.telegram.ui.Components.SlideChooseView;

public class PartisanSlideChooseView extends SlideChooseView {
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
}
