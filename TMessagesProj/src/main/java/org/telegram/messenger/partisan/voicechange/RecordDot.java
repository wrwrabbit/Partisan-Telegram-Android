package org.telegram.messenger.partisan.voicechange;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;

public class RecordDot extends View {

    private float alpha;
    private long lastUpdateTime;
    private boolean isIncreasing;
    private boolean recording;
    private final Paint redDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RecordDot(Context context) {
        super(context);
        int dotColor = Theme.getColor(Theme.key_chat_recordedVoiceDot);
        redDotPaint.setColor(dotColor);
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
        if (!recording) {
            alpha = 0.0f;
            lastUpdateTime = System.currentTimeMillis();
            isIncreasing = false;
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!recording) {
            redDotPaint.setAlpha(0);
            return;
        }
        redDotPaint.setAlpha((int) (255 * alpha));

        long dt = (System.currentTimeMillis() - lastUpdateTime);

        if (!isIncreasing) {
            alpha -= dt / 600.0f;
            if (alpha <= 0) {
                alpha = 0;
                isIncreasing = true;
            }
        } else {
            alpha += dt / 600.0f;
            if (alpha >= 1) {
                alpha = 1;
                isIncreasing = false;
            }
        }

        lastUpdateTime = System.currentTimeMillis();
        canvas.drawCircle(this.getMeasuredWidth() >> 1, this.getMeasuredHeight() >> 1, dp(5), redDotPaint);
        invalidate();
    }
}
