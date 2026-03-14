package org.telegram.messenger.partisan.masked_ptg.calculator;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class CalculatorPasscodeScreen extends AbstractMaskedPasscodeScreen {
    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 80;

    private final int BUTTONS_ROW_COUNT = 5;
    private final int BUTTONS_COL_COUNT = 4;

    private FrameLayout backgroundFrameLayout;
    private String inputString = "";
    private TextView inputTextView;
    private TextView outputTextView;
    private FrameLayout buttonsFrameLayout;
    private ArrayList<Button> buttons;

    public CalculatorPasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        super(context, delegate, unlockingApp);
    }

    @Override
    public View createView() {

        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);

        inputTextView = new TextView(context);
        inputTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        inputTextView.setTextColor(0xFFFFFFFF);
        inputTextView.setEllipsize(TextUtils.TruncateAt.START);
        inputTextView.setSingleLine();
        backgroundFrameLayout.addView(inputTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        outputTextView = new TextView(context);
        outputTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        outputTextView.setTextColor(0xFFFFFFFF);
        backgroundFrameLayout.addView(outputTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 70, 0, 70, 0));

        buttonsFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(buttonsFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        buttons = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Button button = createButton(i);
            if (i == 1) {
                button.setVisibility(View.GONE);
            }
            buttons.add(button);
        }
        for (int a = 19; a >= 0; a--) {
            Button button = buttons.get(a);
            buttonsFrameLayout.addView(button, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT));
        }
        return backgroundFrameLayout;
    }

    private Button createButton(int i) {
        Button button = new Button(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        ScaleStateListAnimator.apply(button, .15f, 1.5f);
        int row = i / BUTTONS_COL_COUNT;
        int col = i % BUTTONS_COL_COUNT;
        button.setTextColor(getButtonTextColor(row, col));
        int backgroundColor = getButtonBackgroundColor(row, col);
        Drawable background = Theme.createSimpleSelectorRoundRectDrawable(dp(BUTTON_SIZE / 2), backgroundColor, backgroundColor);
        button.setBackground(background);
        String buttonText = getButtonText(row, col);
        button.setTag(buttonText);
        button.setText(buttonText);
        button.setOnClickListener(this::onButtonClicked);
        return button;
    }

    private int getButtonBackgroundColor(int row, int col) {
        if (col == BUTTONS_COL_COUNT - 1) {
            return MaskedPtgConfig.getPrimaryColor(context);
        } else if (row == 4) {
            return 0xffa5a5a5;
        } else {
            return 0xff323232;
        }
    }

    private int getButtonTextColor(int row, int col) {
        if (row == BUTTONS_ROW_COUNT - 1 && col != BUTTONS_COL_COUNT - 1) {
            return 0xff060606;
        } else {
            return 0xffffffff;
        }
    }

    private String getButtonText(int row, int col) {
        if (row >= 1 && row < BUTTONS_ROW_COUNT - 1 && col <= 2) {
            int num = (row - 1) * 3 + col + 1;
            return String.valueOf(num);
        } else if (col == 3) {
            if (row == 0) {
                return "=";
            } else if (row == 1) {
                return "+";
            } else if (row == 2) {
                return "-";
            } else if (row == 3) {
                return "×";
            } else if (row == 4) {
                return "/";
            }
        } else if (row == BUTTONS_ROW_COUNT - 1) {
            if (col == 0) {
                return "AC";
            } else if (col == 1) {
                return "⌫";
            } else if (col == 2) {
                return "%";
            }
        } else if (row == 0) {
            if (col == 0) {
                return "0";
            } else if (col == 1) {
                return "";
            } else if (col == 2) {
                return ".";
            }
        }
        return "";
    }

    private void onButtonClicked(View view) {
        String tag = (String) view.getTag();
        if (tag.equals("=")) {
            doEquals();
        } else if (tag.equals("⌫")) {
            deleteLastInputChar();
        } else if (tag.equals("AC")) {
            clearInput();
        } else {
            addCharToInput(tag.charAt(0));
        }
        if (inputTextView.length() == 4) {
            delegate.passcodeEntered(getPasswordString());
        }
    }

    private void doEquals() {
        try {
            String outputString = outputTextView.getText().toString()
                    .replace(getDecimalSeparator(), '.');
            BigDecimal outputValue = new BigDecimal(outputString);
            setInput(removeFractionZeroesFromString(outputValue.toPlainString(), getLocale()));
        } catch (Exception ignore) {
            clearInput();
        }
    }

    private String getPasswordString() {
        return (String) inputTextView.getText();
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        backgroundFrameLayout.setBackgroundColor(0xff000000);

        if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN) {
            buttonsFrameLayout.setVisibility(View.VISIBLE);
            inputTextView.setVisibility(View.VISIBLE);
            outputTextView.setVisibility(View.VISIBLE);
        }
        inputTextView.setText("");
        outputTextView.setText("");
        inputString = "";
        this.tutorialType = tutorialType;
        if (tutorialType == TutorialType.FULL) {
            createInstructionDialog().show();
        }
    }

    private AlertDialog createInstructionDialog() {
        String message = LocaleController.getString(R.string.CalculatorPasscodeScreen_Instruction);
        return createMaskedPasscodeScreenInstructionDialog(message, 0);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        FrameLayout.LayoutParams layoutParams;

        final boolean landscape = !AndroidUtilities.isTablet() && context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        int bottomOffset = 0;
        int topOffset = 0;
        int leftOffset = 0;
        int rightOffset = 0;
        if (Build.VERSION.SDK_INT >= 21) {
            if (landscape) {
                leftOffset = AndroidUtilities.statusBarHeight;
                rightOffset = AndroidUtilities.navigationBarHeight;
            } else {
                bottomOffset = AndroidUtilities.navigationBarHeight;
                topOffset = AndroidUtilities.statusBarHeight;
            }
        }
        int width = View.MeasureSpec.getSize(widthMeasureSpec) - leftOffset - rightOffset;
        int height = AndroidUtilities.displaySize.y - bottomOffset - topOffset;

        int sizeBetweenButtonsX = dp(BUTTON_X_MARGIN);
        int sizeBetweenButtonsY = dp(BUTTON_Y_MARGIN);
        int buttonHeight = dp(BUTTON_SIZE);
        int buttonWidth = dp(BUTTON_SIZE);

        int buttonsSumWidth = 2 * sizeBetweenButtonsX + (buttonWidth + sizeBetweenButtonsX) * 3 + buttonWidth;
        int targetButtonsSumWidth = landscape ? width / 2 : width;
        float scaleX = (float) targetButtonsSumWidth / buttonsSumWidth;

        int buttonsSumHeight = sizeBetweenButtonsY + (buttonHeight + sizeBetweenButtonsY) * 4 + buttonHeight;
        int targetButtonsSumHeight = landscape ? height : (int)(0.65 * height);
        float scaleY = (float) targetButtonsSumHeight / buttonsSumHeight;

        buttonsSumWidth = (int)(buttonsSumWidth * scaleX);
        buttonsSumHeight = (int)(buttonsSumHeight * scaleY);
        sizeBetweenButtonsX = (int)(sizeBetweenButtonsX * scaleX);
        sizeBetweenButtonsY = (int)(sizeBetweenButtonsY * scaleY);
        buttonWidth = (int)(buttonWidth * scaleX);
        buttonHeight = (int)(buttonHeight * scaleY);

        if (buttonHeight > buttonWidth) {
            sizeBetweenButtonsY += (int)(((float)buttonHeight - buttonWidth) * 5 / 6);
            buttonHeight = buttonWidth;
        }

        for (int a = 0; a < 20; a++) {
            int row = a / BUTTONS_COL_COUNT;
            int col = a % BUTTONS_COL_COUNT;
            Button button = buttons.get(a);
            layoutParams = (FrameLayout.LayoutParams) button.getLayoutParams();
            layoutParams.bottomMargin = sizeBetweenButtonsY + bottomOffset + (buttonHeight + sizeBetweenButtonsY) * row;
            layoutParams.leftMargin = (buttonWidth + sizeBetweenButtonsX) * col;

            if (a == 0) {
                layoutParams.width = buttonWidth + sizeBetweenButtonsX + buttonWidth;
            } else {
                layoutParams.width = buttonWidth;
            }
            layoutParams.height = buttonHeight;
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28.0f * Math.min(scaleX, scaleY));

            button.setLayoutParams(layoutParams);
        }

        int textViewsBottomOffset;
        int textViewsRightOffset;

        layoutParams = (FrameLayout.LayoutParams) buttonsFrameLayout.getLayoutParams();
        if (landscape) {
            textViewsBottomOffset = 0;
            textViewsRightOffset = width / 2 + rightOffset;
            layoutParams.width = width / 2;
            layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        } else {
            textViewsBottomOffset = buttonsSumHeight + bottomOffset;
            textViewsRightOffset = 0;
            layoutParams.width =  buttonWidth * BUTTONS_COL_COUNT + sizeBetweenButtonsX * Math.max(0, BUTTONS_COL_COUNT - 1);
            layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        layoutParams.height = dp(82) + targetButtonsSumHeight;
        layoutParams.leftMargin = textViewsRightOffset;
        buttonsFrameLayout.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) inputTextView.getLayoutParams();
        layoutParams.bottomMargin = textViewsBottomOffset + buttonHeight + 2 * sizeBetweenButtonsY + 2 * AndroidUtilities.dp(52);
        layoutParams.leftMargin = sizeBetweenButtonsX;
        layoutParams.rightMargin = textViewsRightOffset + sizeBetweenButtonsX;
        layoutParams.width = LayoutHelper.WRAP_CONTENT;
        inputTextView.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) outputTextView.getLayoutParams();
        layoutParams.bottomMargin = textViewsBottomOffset + buttonHeight + 2 * sizeBetweenButtonsY;
        layoutParams.leftMargin = sizeBetweenButtonsX;
        layoutParams.rightMargin = textViewsRightOffset + sizeBetweenButtonsX;
        layoutParams.width = LayoutHelper.WRAP_CONTENT;
        outputTextView.setLayoutParams(layoutParams);
    }

    private void deleteLastInputChar() {
        if (inputString.isEmpty()) {
            return;
        }
        inputString = inputString.substring(0, inputString.length() - 1);
        inputTextView.setText(inputString);
        updateOutput();
    }

    private void clearInput() {
        inputString = "";
        inputTextView.setText(inputString);
        updateOutput();
    }

    private void setInput(String input) {
        inputString = input;
        inputTextView.setText(inputString);
        updateOutput();
    }

    private void addCharToInput(char c) {
        if (inputString.isEmpty()) {
            if ("+×/%.".contains(String.valueOf(c))) {
                return;
            }
        } else {
            char lastChar = inputString.charAt(inputString.length() - 1);
            if (c == '.') {
                if (!"0123456789".contains(String.valueOf(lastChar))) {
                    return;
                }
                for (int i = inputString.length() - 1; i >= 0; i--) {
                    char currentChar = inputString.charAt(i);
                    if (currentChar == getDecimalSeparator()) {
                        return;
                    } else if (!"0123456789".contains(String.valueOf(currentChar))) {
                        break;
                    }
                }
                c = getDecimalSeparator();
            } else if ("+-×/".contains(String.valueOf(c))) {
                if ("+-×/".contains(String.valueOf(lastChar))) {
                    inputString = inputString.substring(0, inputString.length() - 1);
                }
            } else if (c == '%') {
                if (!"0123456789".contains(String.valueOf(lastChar))) {
                    return;
                }
            }
        }
        inputString += c;
        inputTextView.setText(inputString);
        updateOutput();
    }

    private void updateOutput() {
        try {
            BigDecimal result = CalculatorHelper.calculateExpression(inputString, getDecimalSeparator());
            outputTextView.setText(formatBigDecimal(result, getLocale()));
        } catch (Exception ignore) {
            outputTextView.setText("Error");
        }
    }

    private static String formatBigDecimal(BigDecimal value, Locale locale) {
        if (value == null) {
            return "";
        } else if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        } else {
            String plainOutput = value.toPlainString();
            if (plainOutput.length() <= 10) {
                return removeFractionZeroesFromString(plainOutput, locale)
                        .replace('.', getDecimalSeparator(locale));
            } else {
                String output = String.format(locale, "%." + Math.min(value.precision(), 7) + "g", value);
                return removeFractionZeroesFromString(output, locale);
            }
        }
    }

    private static String removeFractionZeroesFromString(String output, Locale locale) {
        char decimalSeparator = getDecimalSeparator(locale);
        if (output.contains(String.valueOf(decimalSeparator))) {
            while (output.endsWith("0")) {
                output = output.substring(0, output.length() - 1);
            }
        }
        if (output.endsWith(String.valueOf(decimalSeparator))) {
            output = output.substring(0, output.length() - 1);
        }
        return output;
    }

    private char getDecimalSeparator() {
        return getDecimalSeparator(getLocale());
    }

    private static char getDecimalSeparator(Locale locale) {
        return DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
    }

    private Locale getLocale() {
        return context.getResources().getConfiguration().locale;
    }

    @Override
    public void onPasscodeError() {
        if (tutorialType != TutorialType.DISABLED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreen_Tutorial));
            builder.setMessage(LocaleController.getString(R.string.MaskedPasscodeScreen_WrongPasscode));
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.create().show();
        }
    }
}
