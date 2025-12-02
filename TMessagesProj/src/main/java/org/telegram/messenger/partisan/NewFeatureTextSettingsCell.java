package org.telegram.messenger.partisan;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

public class NewFeatureTextSettingsCell extends TextSettingsCell {
    private TextView newTextView;

    public NewFeatureTextSettingsCell(Context context) {
        this(context, null);
    }

    public NewFeatureTextSettingsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        newTextView = new TextView(context);
        newTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        newTextView.setLines(1);
        newTextView.setMaxLines(1);
        newTextView.setSingleLine(true);
        newTextView.setEllipsize(TextUtils.TruncateAt.END);
        newTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        newTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        newTextView.setText("new");
        addView(newTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 0, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        int width = availableWidth / 2;
        if (newTextView.getVisibility() == VISIBLE) {
            newTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            MarginLayoutParams params = (MarginLayoutParams) newTextView.getLayoutParams();
            int textViewLeftMargin = ((MarginLayoutParams)textView.getLayoutParams()).leftMargin;
            int textViewWidth = (int)textView.getPaint().measureText(textView.getText().toString());

            params.topMargin = -AndroidUtilities.dp(3);
            params.leftMargin = textViewLeftMargin + textViewWidth + AndroidUtilities.dp(3);
        }
    }
}
