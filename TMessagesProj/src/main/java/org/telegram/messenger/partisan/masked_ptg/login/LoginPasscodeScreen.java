package org.telegram.messenger.partisan.masked_ptg.login;

import static org.telegram.messenger.AndroidUtilities.dp;
import android.os.Handler;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class LoginPasscodeScreen extends AbstractMaskedPasscodeScreen {
    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 80;

    private RelativeLayout backgroundFrameLayout;
    private String inputString = "";
    private TextView inputTextView;
    private EditText loginTextView;
    private EditText passwordTextView;
    private Button loginButton;

    private FrameLayout buttonsFrameLayout;
    private ArrayList<Button> buttons;

    public LoginPasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        super(context, delegate, unlockingApp);
    }

    @Override
    public View createView() {
        backgroundFrameLayout = new RelativeLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);

        // Input text view
        inputTextView = new TextView(context);
        inputTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        inputTextView.setTextColor(0xff000000);
        inputTextView.setEllipsize(TextUtils.TruncateAt.START);
        inputTextView.setSingleLine();

        // Add inputTextView with proper margins
        RelativeLayout.LayoutParams inputParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        inputParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        inputParams.bottomMargin = AndroidUtilities.dp(500); // Set bottom margin for vertical positioning
        inputTextView.setLayoutParams(inputParams);
        backgroundFrameLayout.addView(inputTextView);

        // Login text view (EditText)
        loginTextView = new EditText(context);
        loginTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        loginTextView.setTextColor(0xff000000);
        loginTextView.setEllipsize(TextUtils.TruncateAt.START);
        loginTextView.setSingleLine();
        loginTextView.setWidth(300);
        loginTextView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setStroke(2, Color.GRAY);
        border.setCornerRadius(8);
        loginTextView.setBackground(border);

        RelativeLayout.LayoutParams loginParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        loginParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        loginParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        int margin = AndroidUtilities.dp(32);
        loginParams.setMargins(margin, 0, margin, AndroidUtilities.dp(430));
        loginTextView.setLayoutParams(loginParams);
        backgroundFrameLayout.addView(loginTextView);

        // Password text view
        passwordTextView = new EditText(context);
        passwordTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        passwordTextView.setTextColor(0xff000000);
        passwordTextView.setEllipsize(TextUtils.TruncateAt.START);
        passwordTextView.setSingleLine();
        passwordTextView.setWidth(300);
        passwordTextView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        GradientDrawable border_2 = new GradientDrawable();
        border_2.setColor(Color.TRANSPARENT);
        border_2.setStroke(2, Color.GRAY);
        border_2.setCornerRadius(8);
        passwordTextView.setBackground(border_2);

        RelativeLayout.LayoutParams passwordParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        passwordParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        passwordParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        passwordParams.setMargins(margin, 0, margin, AndroidUtilities.dp(360));
        passwordTextView.setLayoutParams(passwordParams);
        backgroundFrameLayout.addView(passwordTextView);

        // Login button (below password EditText)
        loginButton = new Button(context);
        loginButton.setText("Login");
        loginButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        loginButton.setBackgroundColor(Color.parseColor("#4CAF50")); // Example: green button
        loginButton.setTextColor(Color.WHITE);
        loginButton.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        buttonParams.setMargins(margin, 0, margin, AndroidUtilities.dp(290)); // Adjust bottom margin as needed
        loginButton.setLayoutParams(buttonParams);
        backgroundFrameLayout.addView(loginButton);

        // Set OnClickListener for the button
        final int originalColor = Color.parseColor("#4CAF50");  // Green button color
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Change the button color to dark green
                v.setBackgroundColor(Color.parseColor("#388E3C"));  // Dark green color

                // Use a Handler to reset the button color back after a short delay (e.g., 200ms)
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        v.setBackgroundColor(originalColor);  // Reset to the original color
                    }
                }, 200);  // 200ms delay (adjust the time as needed)

                // Your existing login logic here, if any
                // For example, validating input or performing a login action
            }
        });

        return backgroundFrameLayout;
    }

    private void onButtonClicked(View view) {

//        String tag = (String) view.getTag();
//        if (tag.equals("=")) {
//            doEquals();
//        } else if (tag.equals("⌫")) {
//            deleteLastInputChar();
//        } else if (tag.equals("AC")) {
//            clearInput();
//        } else {
//            addCharToInput(tag.charAt(0));
//        }
//        if (inputTextView.length() == 4) {
//            delegate.passcodeEntered(getPasswordString());
//        }
    }

    private void doEquals() {
        try {
            int k = 0;
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
        backgroundFrameLayout.setBackgroundColor(0xffffffff);

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
//            buttonsFrameLayout.setVisibility(View.VISIBLE);
            inputTextView.setVisibility(View.VISIBLE);
            loginTextView.setVisibility(View.VISIBLE);
            passwordTextView.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);
        }
        inputTextView.setText("Enter username and password");
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

//    @Override
//    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        RelativeLayout.LayoutParams layoutParams;
//
//        // Input TextView: Positioned first with a bottom margin
//        layoutParams = new RelativeLayout.LayoutParams(
//                LayoutHelper.WRAP_CONTENT,
//                LayoutHelper.WRAP_CONTENT
//        );
//        layoutParams.topMargin = AndroidUtilities.dp(100); // Adjust top margin to position it vertically
//        inputTextView.setLayoutParams(layoutParams);
//
//        // Output TextView: Positioned below the Input TextView
//        layoutParams = new RelativeLayout.LayoutParams(
//                LayoutHelper.WRAP_CONTENT,
//                LayoutHelper.WRAP_CONTENT
//        );
//        layoutParams.topMargin = AndroidUtilities.dp(300); // Adjust top margin to create space from the previous view
//        outputTextView.setLayoutParams(layoutParams);
//
//        // Login TextView: Positioned below the Output TextView
//        layoutParams = new RelativeLayout.LayoutParams(
//                LayoutHelper.WRAP_CONTENT,
//                LayoutHelper.WRAP_CONTENT
//        );
//        layoutParams.topMargin = AndroidUtilities.dp(500); // Adjust top margin to create space from the previous view
//        loginTextView.setLayoutParams(layoutParams);
//    }

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
