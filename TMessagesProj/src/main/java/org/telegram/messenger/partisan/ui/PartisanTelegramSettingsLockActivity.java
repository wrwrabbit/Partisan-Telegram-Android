package org.telegram.messenger.partisan.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CodeFieldContainer;
import org.telegram.ui.CodeNumberField;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.PTelegramSettingsFragment;

public class PartisanTelegramSettingsLockActivity extends BaseFragment {

    private RLottieImageView lockImageView;
    private TextViewSwitcher descriptionTextSwitcher;
    private OutlineTextContainerView outlinePasswordView;
    private EditTextBoldCursor passwordEditText;
    private CodeFieldContainer codeFieldContainer;
    private CustomPhoneKeyboardView keyboardView;
    private FragmentFloatingButton floatingButton;
    private Runnable onShowKeyboardCallback;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        scrollView.setFillViewport(true);

        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int frameBottom;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() >= dp(20)) {
                    if (isCustomKeyboardVisible()) {
                        scrollView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) + measureKeyboardHeight());
                    } else {
                        scrollView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                    }
                } else if (keyboardView.getVisibility() != View.GONE) {
                    scrollView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                } else {
                    scrollView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                }
                keyboardView.layout(0, frameBottom, getMeasuredWidth(), frameBottom + dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                notifyHeightChanged();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                int frameHeight = height;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() < dp(20)) {
                    frameHeight -= dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                }
                scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
                keyboardView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP), MeasureSpec.EXACTLY));
            }
        };
        contentView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight >= dp(20) && onShowKeyboardCallback != null) {
                onShowKeyboardCallback.run();
                onShowKeyboardCallback = null;
            }
        });
        fragmentView = contentView;
        contentView.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setVisibility(isCustomKeyboardVisible() ? View.VISIBLE : View.GONE);
        contentView.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        LinearLayout innerLinearLayout = new LinearLayout(context);
        innerLinearLayout.setOrientation(LinearLayout.VERTICAL);
        innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        frameLayout.addView(innerLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        lockImageView = new RLottieImageView(context);
        lockImageView.setFocusable(false);
        lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
        lockImageView.setAutoRepeat(false);
        lockImageView.playAnimation();
        lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
        innerLinearLayout.addView(lockImageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        TextView titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setText(LocaleController.getString(R.string.EnterMainPasscode));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        descriptionTextSwitcher = new TextViewSwitcher(context);
        descriptionTextSwitcher.setFactory(() -> {
            TextView tv = new TextView(context);
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setLineSpacing(dp(2), 1);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            return tv;
        });
        descriptionTextSwitcher.setInAnimation(context, R.anim.alpha_in);
        descriptionTextSwitcher.setOutAnimation(context, R.anim.alpha_out);
        descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterMainPasscodeInfo));
        innerLinearLayout.addView(descriptionTextSwitcher, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 8, 20, 0));

        FrameLayout codeContainer = new FrameLayout(context);

        outlinePasswordView = new OutlineTextContainerView(context);
        outlinePasswordView.setText(LocaleController.getString(R.string.EnterPassword));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        passwordEditText.setBackground(null);
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        passwordEditText.setSingleLine(true);
        passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        passwordEditText.setCursorSize(dp(20));
        passwordEditText.setCursorWidth(1.5f);
        int padding = dp(16);
        passwordEditText.setPadding(padding, padding, padding, padding);
        passwordEditText.setOnFocusChangeListener((v, hasFocus) -> outlinePasswordView.animateSelection(hasFocus ? 1 : 0));
        passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            processDone();
            return true;
        });

        outlinePasswordView.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        codeContainer.addView(outlinePasswordView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 0));

        codeFieldContainer = new CodeFieldContainer(context) {
            @Override
            protected void processNextPressed() {
                processDone();
            }
        };
        codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
            f.setTransformationMethod(PasswordTransformationMethod.getInstance());
            f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            f.setOnFocusChangeListener((v, hasFocus) -> {
                keyboardView.setEditText(f);
                keyboardView.setDispatchBackWhenEmpty(true);
            });
        }
        codeContainer.addView(codeFieldContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 10, 40, 0));

        innerLinearLayout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 72));

        floatingButton = new FragmentFloatingButton(context, resourceProvider);
        VerticalPositionAutoAnimator.attach(floatingButton);
        frameLayout.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParamsBig());
        floatingButton.setOnClickListener(view -> processDone());

        TransformableLoginButtonView floatingButtonIcon = new TransformableLoginButtonView(context);
        floatingButtonIcon.setTransformType(TransformableLoginButtonView.TRANSFORM_ARROW_CHECK);
        floatingButtonIcon.setProgress(0f);
        floatingButtonIcon.setColor(Theme.getColor(Theme.key_chats_actionIcon));
        floatingButtonIcon.setDrawBackground(false);
        floatingButton.setContentDescription(LocaleController.getString(R.string.Next));
        floatingButton.addView(floatingButtonIcon, LayoutHelper.createFrame(56, 56, Gravity.CENTER));
        floatingButton.addAdditionalView(floatingButton);

        updateInputVisibility(false);

        return fragmentView;
    }

    private boolean isPinCode() {
        return SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN;
    }

    private boolean isCustomKeyboardVisible() {
        return isPinCode() && !AndroidUtilities.isTablet() &&
                AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y &&
                !AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private void updateInputVisibility(boolean animate) {
        if (isPinCode()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, true, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, false, 1f, animate);
            floatingButton.setButtonVisible(false, animate);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, false, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, true, 1f, animate);
            onShowKeyboardCallback = () -> {
                floatingButton.setButtonVisible(true, animate);
                AndroidUtilities.cancelRunOnUIThread(onShowKeyboardCallback);
            };
            AndroidUtilities.runOnUIThread(onShowKeyboardCallback, 3000);
        }
        setCustomKeyboardVisible(isCustomKeyboardVisible(), animate);
        showKeyboard();
    }

    private void setCustomKeyboardVisible(boolean visible, boolean animate) {
        if (visible) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        } else {
            AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
        }
        if (!animate) {
            keyboardView.setVisibility(visible ? View.VISIBLE : View.GONE);
            keyboardView.setAlpha(visible ? 1 : 0);
            keyboardView.setTranslationY(visible ? 0 : dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
            if (fragmentView != null) {
                fragmentView.requestLayout();
            }
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? CubicBezierInterpolator.DEFAULT : Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                keyboardView.setAlpha(val);
                keyboardView.setTranslationY((1f - val) * dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) * 0.75f);
                fragmentView.requestLayout();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) keyboardView.setVisibility(View.VISIBLE);
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) keyboardView.setVisibility(View.GONE);
                }
            });
            animator.start();
        }
    }

    private void showKeyboard() {
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
            if (!isCustomKeyboardVisible()) {
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[0]);
            }
        } else {
            passwordEditText.requestFocus();
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void processDone() {
        String password = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();

        if (SharedConfig.passcodeRetryInMs > 0) {
            int value = Math.max(1, (int) Math.ceil(SharedConfig.passcodeRetryInMs / 1000.0));
            Toast.makeText(getParentActivity(), LocaleController.formatString(R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)), Toast.LENGTH_SHORT).show();
            clearInput();
            onPasscodeError();
            return;
        }

        SharedConfig.PasscodeCheckResult result = SharedConfig.checkPasscode(password);
        if (!result.isRealPasscodeSuccess) {
            BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.PasscodeSettingsType, false);
            SharedConfig.addBadPasscodeAttempt(badAttempt);
            badAttempt.takePhotos(getParentActivity());
            SharedConfig.increaseBadPasscodeTries();
            clearInput();
            onPasscodeError();
            return;
        }

        SharedConfig.badPasscodeTries = 0;
        SharedConfig.saveConfig();

        passwordEditText.clearFocus();
        AndroidUtilities.hideKeyboard(passwordEditText);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.clearFocus();
            AndroidUtilities.hideKeyboard(f);
        }
        keyboardView.setEditText(null);

        animateSuccessAnimation(() -> presentFragment(new PTelegramSettingsFragment(), true));
    }

    private void clearInput() {
        passwordEditText.setText("");
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setText("");
        }
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
        }
    }

    private void animateSuccessAnimation(Runnable callback) {
        if (!isPinCode()) {
            callback.run();
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            CodeNumberField field = codeFieldContainer.codeField[i];
            field.postDelayed(() -> field.animateSuccessProgress(1f), i * 75L);
        }
        codeFieldContainer.postDelayed(() -> {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateSuccessProgress(0f);
            }
            callback.run();
        }, codeFieldContainer.codeField.length * 75L + 350L);
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) return;
        try {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
        if (isPinCode()) {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateErrorProgress(1f);
            }
        } else {
            outlinePasswordView.animateError(1f);
        }
        AndroidUtilities.shakeViewSpring(isPinCode() ? codeFieldContainer : outlinePasswordView, isPinCode() ? 10 : 4, () -> AndroidUtilities.runOnUIThread(() -> {
            if (isPinCode()) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateErrorProgress(0f);
                }
            } else {
                outlinePasswordView.animateError(0f);
            }
        }, isPinCode() ? 150 : 1000));
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isCustomKeyboardVisible()) {
            AndroidUtilities.runOnUIThread(this::showKeyboard, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (isCustomKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            showKeyboard();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setCustomKeyboardVisible(isCustomKeyboardVisible(), false);
        if (lockImageView != null) {
            lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
        }
        if (codeFieldContainer != null && codeFieldContainer.codeField != null) {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
            }
        }
    }
}
