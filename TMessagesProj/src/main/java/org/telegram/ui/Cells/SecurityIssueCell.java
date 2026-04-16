package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PrivacyChecker;
import org.telegram.messenger.partisan.SecurityIssue;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.TwoStepVerificationSetupActivity;

public abstract class SecurityIssueCell extends LinearLayout {
    private final TextView headerTextView;
    private final TextView detailTextView;
    private final TextView fixButton;
    private final ImageView closeButton;
    protected SecurityIssue currentIssue;
    private boolean needDivider;

    public SecurityIssueCell(Context context) {
        super(context);
        setOrientation(VERTICAL);

        LinearLayout horizontalLayout = new LinearLayout(context);
        horizontalLayout.setOrientation(HORIZONTAL);
        addView(horizontalLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,0, 0, 0, 0));

        LinearLayout verticalLayout = new LinearLayout(context);
        verticalLayout.setOrientation(VERTICAL);
        horizontalLayout.addView(verticalLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 0, 0));

        headerTextView = new TextView(context);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        headerTextView.setEllipsize(TextUtils.TruncateAt.END);
        headerTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        verticalLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 15, 0, 0));

        detailTextView = new TextView(context);
        detailTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        detailTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        detailTextView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
        detailTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        detailTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        verticalLayout.addView(detailTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 21, 8, 0, 15));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_close);
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_color_red), PorterDuff.Mode.MULTIPLY));
        closeButton.setImageDrawable(drawable);
        closeButton.setFocusable(true);
        closeButton.setContentDescription(LocaleController.getString(R.string.Close));
        closeButton.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(24), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector)));
        closeButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        closeButton.setOnClickListener(v -> onCloseClick());
        horizontalLayout.addView(closeButton, LayoutHelper.createLinear(48, 48, 0.0f, Gravity.END | Gravity.TOP, 0, 0, 0, 0));

        fixButton = new TextView(context);
        fixButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 24));
        fixButton.setLines(1);
        fixButton.setSingleLine(true);
        fixButton.setGravity(Gravity.CENTER_HORIZONTAL);
        fixButton.setEllipsize(TextUtils.TruncateAt.END);
        fixButton.setGravity(Gravity.CENTER);
        fixButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        fixButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        fixButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(fixButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 21, 0, 21, 15));
        fixButton.setText(LocaleController.getString(R.string.FixIssueButton));
        fixButton.setOnClickListener(v -> onFixClick());
    }

    public void setIssue(SecurityIssue issue, boolean divider) {
        currentIssue = issue;
        needDivider = divider;
        switch (issue) {
            case ROOT:
                headerTextView.setText(LocaleController.getString(R.string.RootIssueTitle));
                detailTextView.setText(LocaleController.getString(R.string.RootIssueText));
                fixButton.setVisibility(View.GONE);
                break;
            case USB_DEBUGGING:
                headerTextView.setText(LocaleController.getString(R.string.UsbDebuggingIssueTitle));
                detailTextView.setText(LocaleController.getString(R.string.UsbDebuggingIssueText));
                fixButton.setVisibility(View.GONE);
                break;
            case TWO_STEP_VERIFICATION:
                headerTextView.setText(LocaleController.getString(R.string.TwoStepAutentificationIssueTitle));
                detailTextView.setText(LocaleController.getString(R.string.TwoStepAutentificationIssueText));
                fixButton.setVisibility(View.VISIBLE);
                break;
            case PRIVACY:
                headerTextView.setText(LocaleController.getString(R.string.PrivacyIssueTitle));
                detailTextView.setText(LocaleController.getString(R.string.PrivacyIssueText));
                fixButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    protected abstract void onCloseClick();

    protected abstract void onFixClick();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec + (needDivider ? 1 : 0));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (needDivider) {
            int offset = 20;
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
