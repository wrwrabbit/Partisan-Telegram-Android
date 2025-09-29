package org.telegram.messenger.partisan.masked_ptg.login;

import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import android.os.Handler;


import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

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
    private RelativeLayout backgroundFrameLayout;
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
        loginTextView.setHint("Enter username");
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
        passwordTextView.setHint("Enter password");
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
        loginButton.setBackgroundColor(MaskedPtgConfig.getPrimaryColor(context)); // Example: green button
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
        final int originalColor = MaskedPtgConfig.getPrimaryColor(context);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Change the button color to dark green
                float[] hsv = new float[3];
                Color.colorToHSV(originalColor, hsv);
                hsv[2] *= 0.8; // value/brightness, 1 = original, <1 = darker
                int newColor = Color.HSVToColor(hsv);
                v.setBackgroundColor(newColor);

                // Use a Handler to reset the button color back after a short delay (e.g., 200ms)
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        v.setBackgroundColor(originalColor);  // Reset to the original color
                    }
                }, 200);  // 200ms delay (adjust the time as needed)

                delegate.passcodeEntered(getPasswordString());
            }
        });

        return backgroundFrameLayout;
    }
    private String getPasswordString() {
        return passwordTextView.getText().toString();
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            parentActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        backgroundFrameLayout.setBackgroundColor(0xffffffff);

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
            inputTextView.setVisibility(View.VISIBLE);
            loginTextView.setVisibility(View.VISIBLE);
            passwordTextView.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);
        }
        inputTextView.setText("Login using username and password");
        this.tutorialType = tutorialType;
        if (tutorialType == TutorialType.FULL) {
            createInstructionDialog().show();
        }
    }

    private AlertDialog createInstructionDialog() {
        String message = LocaleController.getString(R.string.LoginPasscodeScreen_Instruction);
        return createMaskedPasscodeScreenInstructionDialog(message, 0);
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
