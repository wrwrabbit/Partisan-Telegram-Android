package org.telegram.messenger.partisan.masked_ptg.original;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class OriginalPasscodeScreen extends AbstractMaskedPasscodeScreen {
    private final static float BACKGROUND_SPRING_STIFFNESS = 300f;

    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 60;

    private FrameLayout passwordFrameLayout;
    private FrameLayout backgroundFrameLayout;
    private Drawable backgroundDrawable;
    private TextView passcodeTextView;
    private TextView retryTextView;
    private ImageView checkImage;
    private ImageView fingerprintImage;
    private View border;
    private EditTextBoldCursor passwordEditText;
    private AnimatingTextView passwordEditText2;
    private SpringAnimation backgroundAnimationSpring;
    private LinkedList<Runnable> backgroundSpringQueue = new LinkedList<>();
    private LinkedList<Boolean> backgroundSpringNextQueue = new LinkedList<>();
    private FrameLayout numbersContainer;
    public FrameLayout numbersFrameLayout;
    private TextView subtitleView;
    private FrameLayout numbersTitleContainer;
    private PasscodeButton fingerprintView;
    private ArrayList<FrameLayout> numberFrameLayouts;
    private int backgroundFrameLayoutColor;
    private boolean showed = false;

    public OriginalPasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        super(context, delegate, unlockingApp);
    }

    @Override
    public View createView() {
        backgroundFrameLayout = new FrameLayout(context) {

            private Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                if (backgroundDrawable != null) {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable || backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        backgroundDrawable.draw(canvas);
                    } else {
                        float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                        float scaleY = (float) (getMeasuredHeight()) / (float) backgroundDrawable.getIntrinsicHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                        int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (getMeasuredHeight() - height) / 2;
                        backgroundDrawable.setBounds(x, y, x + width, y + height);
                        backgroundDrawable.draw(canvas);
                    }
                } else {
                    super.onDraw(canvas);
                }
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            }

            @Override
            public void setBackgroundColor(int color) {
                paint.setColor(color);
            }
        };
        backgroundFrameLayout.setWillNotDraw(false);

        passwordFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        passcodeTextView = new TextView(context);
        passcodeTextView.setTextColor(0xffffffff);
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18.33f);
        passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        passcodeTextView.setTypeface(AndroidUtilities.bold());
        passcodeTextView.setAlpha(0f);
        passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 128));

        retryTextView = new TextView(context);
        retryTextView.setTextColor(0xffffffff);
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        retryTextView.setVisibility(View.INVISIBLE);
        backgroundFrameLayout.addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        passwordEditText2 = new AnimatingTextView(context);
        passwordFrameLayout.addView(passwordEditText2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 46));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
        passwordEditText.setTextColor(0xffffffff);
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setBackgroundDrawable(null);
        passwordEditText.setCursorColor(0xffffffff);
        passwordEditText.setCursorSize(dp(32));
        passwordFrameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 0));
        passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                delegate.passcodeEntered(getPasswordString());
                return true;
            }
            return false;
        });
        passwordEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    boolean needAnimation = false;
                    MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                    motionBackgroundDrawable.setAnimationProgressProvider(null);
                    float progress = motionBackgroundDrawable.getPosAnimationProgress();
                    boolean next;
                    if (count == 0 && after == 1) {
                        motionBackgroundDrawable.switchToNextPosition(true);
                        needAnimation = true;
                        next = true;
                    } else if (count == 1 && after == 0) {
                        motionBackgroundDrawable.switchToPrevPosition(true);
                        needAnimation = true;
                        next = false;
                    } else {
                        next = false;
                    }

                    if (needAnimation) {
                        if (progress >= 1f) {
                            animateBackground(motionBackgroundDrawable);
                        } else {
                            backgroundSpringQueue.offer(()-> {
                                if (next) {
                                    motionBackgroundDrawable.switchToNextPosition(true);
                                } else {
                                    motionBackgroundDrawable.switchToPrevPosition(true);
                                }
                                animateBackground(motionBackgroundDrawable);
                            });
                            backgroundSpringNextQueue.offer(next);

                            List<Runnable> remove = new ArrayList<>();
                            List<Integer> removeIndex = new ArrayList<>();
                            for (int i = 0; i < backgroundSpringQueue.size(); i++) {
                                Runnable callback = backgroundSpringQueue.get(i);
                                boolean qNext = backgroundSpringNextQueue.get(i);

                                if (qNext != next) {
                                    remove.add(callback);
                                    removeIndex.add(i);
                                }
                            }
                            for (Runnable callback : remove) {
                                backgroundSpringQueue.remove(callback);
                            }
                            for (int i : removeIndex) {
                                if (i < backgroundSpringNextQueue.size()) {
                                    backgroundSpringNextQueue.remove(i);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (passwordEditText.length() == 4 && SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN) {
                    delegate.passcodeEntered(getPasswordString());
                }
            }
        });
        passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });

        checkImage = new ImageView(context);
        checkImage.setImageResource(R.drawable.passcode_check);
        checkImage.setScaleType(ImageView.ScaleType.CENTER);
        checkImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(checkImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 4));
        checkImage.setContentDescription(LocaleController.getString("Done", R.string.Done));
        checkImage.setOnClickListener(v -> delegate.passcodeEntered(getPasswordString()));

        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageResource(R.drawable.fingerprint);
        fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
        fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(fingerprintImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT, 10, 0, 0, 4));
        fingerprintImage.setContentDescription(LocaleController.getString("AccDescrFingerprint", R.string.AccDescrFingerprint));

        border = new View(context);
        border.setBackgroundColor(0x30FFFFFF);
        passwordFrameLayout.addView(border, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        numbersContainer = new FrameLayout(context);
        backgroundFrameLayout.addView(numbersContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        numbersFrameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if ((getParent() instanceof View)) {
                    int parentHeight = ((View) getParent()).getHeight();
                    int height = getHeight();
                    float scale = Math.min((float) parentHeight / height, 1f);

                    setPivotX(getWidth() / 2f);
                    setPivotY(((LayoutParams) getLayoutParams()).gravity == Gravity.CENTER ? getHeight() / 2f : 0);
                    setScaleX(scale);
                    setScaleY(scale);
                }
            }
        };
        numbersContainer.addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        numbersTitleContainer = new FrameLayout(context);
        numbersFrameLayout.addView(numbersTitleContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        title.setText(LocaleController.getString(R.string.UnlockToUse));
        numbersTitleContainer.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(0xFFFFFFFF);
        subtitleView.setText(LocaleController.getString(R.string.EnterPINorFingerprint));
        numbersTitleContainer.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 23, 0, 0));

        numberFrameLayouts = new ArrayList<>(10);
        int[] ids = {
                R.id.passcode_btn_0,
                R.id.passcode_btn_1,
                R.id.passcode_btn_2,
                R.id.passcode_btn_3,
                R.id.passcode_btn_4,
                R.id.passcode_btn_5,
                R.id.passcode_btn_6,
                R.id.passcode_btn_7,
                R.id.passcode_btn_8,
                R.id.passcode_btn_9,
                R.id.passcode_btn_backspace,
                R.id.passcode_btn_fingerprint
        };

        for (int a = 0; a < 12; a++) {
            PasscodeButton frameLayout = new PasscodeButton(context);
            ScaleStateListAnimator.apply(frameLayout, .15f, 1.5f);
            frameLayout.setTag(a);
            if (a == 11) {
                frameLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, 0x26ffffff));
                frameLayout.setImage(R.drawable.filled_clear);
                frameLayout.setOnLongClickListener(v -> {
                    passwordEditText.setText("");
                    passwordEditText2.eraseAllCharacters(true);
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        ((MotionBackgroundDrawable) backgroundDrawable).switchToPrevPosition(true);
                    }
                    return true;
                });
                frameLayout.setContentDescription(LocaleController.getString(R.string.AccDescrBackspace));
                setNextFocus(frameLayout, R.id.passcode_btn_0);
            } else if (a == 10) {
                fingerprintView = frameLayout;
                frameLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, 0x26ffffff));
                frameLayout.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
                frameLayout.setImage(R.drawable.fingerprint);
                setNextFocus(frameLayout, R.id.passcode_btn_1);
            } else {
                frameLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0x26ffffff, 0x4cffffff));
                frameLayout.setContentDescription(a + "");
                frameLayout.setNum(a);
                if (a == 0) {
                    setNextFocus(frameLayout, R.id.passcode_btn_backspace);
                } else if (a == 9) {
                    setNextFocus(frameLayout, R.id.passcode_btn_0);
                } else {
                    setNextFocus(frameLayout, ids[a + 1]);
                }
            }
            frameLayout.setId(ids[a]);
            frameLayout.setOnClickListener(v -> {
                int tag = (Integer) v.getTag();
                boolean erased = false;
                switch (tag) {
                    case 0:
                        passwordEditText2.appendCharacter("0");
                        break;
                    case 1:
                        passwordEditText2.appendCharacter("1");
                        break;
                    case 2:
                        passwordEditText2.appendCharacter("2");
                        break;
                    case 3:
                        passwordEditText2.appendCharacter("3");
                        break;
                    case 4:
                        passwordEditText2.appendCharacter("4");
                        break;
                    case 5:
                        passwordEditText2.appendCharacter("5");
                        break;
                    case 6:
                        passwordEditText2.appendCharacter("6");
                        break;
                    case 7:
                        passwordEditText2.appendCharacter("7");
                        break;
                    case 8:
                        passwordEditText2.appendCharacter("8");
                        break;
                    case 9:
                        passwordEditText2.appendCharacter("9");
                        break;
                    case 10:
                        //checkFingerprint();
                        break;
                    case 11:
                        erased = passwordEditText2.eraseLastCharacter();
                        break;
                }
                if (passwordEditText2.length() == 4) {
                    delegate.passcodeEntered(getPasswordString());
                }
                if (tag == 11) {

                } else {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                        motionBackgroundDrawable.setAnimationProgressProvider(null);
                        boolean needAnimation = false;
                        float progress = motionBackgroundDrawable.getPosAnimationProgress();
                        boolean next;
                        if (tag == 10) {
                            if (erased) {
                                motionBackgroundDrawable.switchToPrevPosition(true);
                                needAnimation = true;
                            }
                            next = false;
                        } else {
                            motionBackgroundDrawable.switchToNextPosition(true);
                            needAnimation = true;
                            next = true;
                        }

                        if (needAnimation) {
                            if (progress >= 1f) {
                                animateBackground(motionBackgroundDrawable);
                            } else {
                                backgroundSpringQueue.offer(()-> {
                                    if (next) {
                                        motionBackgroundDrawable.switchToNextPosition(true);
                                    } else {
                                        motionBackgroundDrawable.switchToPrevPosition(true);
                                    }
                                    animateBackground(motionBackgroundDrawable);
                                });
                                backgroundSpringNextQueue.offer(next);

                                List<Runnable> remove = new ArrayList<>();
                                List<Integer> removeIndex = new ArrayList<>();
                                for (int i = 0; i < backgroundSpringQueue.size(); i++) {
                                    Runnable callback = backgroundSpringQueue.get(i);
                                    Boolean qNext = backgroundSpringNextQueue.get(i);

                                    if (qNext != null && qNext != next) {
                                        remove.add(callback);
                                        removeIndex.add(i);
                                    }
                                }
                                for (Runnable callback : remove) {
                                    backgroundSpringQueue.remove(callback);
                                }
                                Collections.sort(removeIndex, (o1, o2) -> o2 - o1);
                                for (int i : removeIndex) {
                                    backgroundSpringNextQueue.remove(i);
                                }
                            }
                        }
                    }
                }
            });
            numberFrameLayouts.add(frameLayout);
        }
        for (int a = 11; a >= 0; a--) {
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            numbersFrameLayout.addView(frameLayout, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.TOP | Gravity.LEFT));
        }
        checkFingerprintButton();
        return backgroundFrameLayout;
    }

    private String getPasswordString() {
        if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN) {
            return passwordEditText2.getString();
        } else if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            return passwordEditText.getText().toString();
        } else {
            return "";
        }
    }


    public static class PasscodeButton extends FrameLayout {

        private final ImageView imageView;
        private final TextView textView1, textView2;

        public PasscodeButton(@NonNull Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.fingerprint);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            textView1 = new TextView(context);
            textView1.setTypeface(AndroidUtilities.bold());
            textView1.setTextColor(0xffffffff);
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            textView1.setGravity(Gravity.CENTER);
            addView(textView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -5.33f, 0, 0));

            textView2 = new TextView(context);
            textView2.setTypeface(AndroidUtilities.bold());
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            textView2.setTextColor(0x7fffffff);
            textView2.setGravity(Gravity.CENTER);
            addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));
        }

        public void setImage(int resId) {
            imageView.setVisibility(View.VISIBLE);
            textView1.setVisibility(View.GONE);
            textView2.setVisibility(View.GONE);
            imageView.setImageResource(resId);
        }

        public void setNum(int num) {
            imageView.setVisibility(View.GONE);
            textView1.setVisibility(View.VISIBLE);
            textView2.setVisibility(View.VISIBLE);
            textView1.setText("" + num);
            textView2.setText(letter(num));
        }

        public static String letter(int num) {
            switch (num) {
                case 0: return "+";
                case 2: return "ABC";
                case 3: return "DEF";
                case 4: return "GHI";
                case 5: return "JKL";
                case 6: return "MNO";
                case 7: return "PQRS";
                case 8: return "TUV";
                case 9: return "WXYZ";
            }
            return "";
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
        }
    }

    private class AnimatingTextView extends FrameLayout {

        private ArrayList<TextView> characterTextViews;
        private ArrayList<TextView> dotTextViews;
        private StringBuilder stringBuilder;
        private final static String DOT = "\u2022";
        private AnimatorSet currentAnimation;
        private Runnable dotRunnable;

        public AnimatingTextView(Context context) {
            super(context);
            characterTextViews = new ArrayList<>(4);
            dotTextViews = new ArrayList<>(4);
            stringBuilder = new StringBuilder(4);

            for (int a = 0; a < 4; a++) {
                TextView textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                characterTextViews.add(textView);

                textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setText(DOT);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                dotTextViews.add(textView);
            }
        }

        private int getXForTextView(int pos) {
            return (getMeasuredWidth() - stringBuilder.length() * dp(30)) / 2 + pos * dp(30) - dp(10);
        }

        public void appendCharacter(String c) {
            if (stringBuilder.length() == 4) {
                return;
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception e) {
                FileLog.e(e);
            }


            ArrayList<Animator> animators = new ArrayList<>();
            final int newPos = stringBuilder.length();
            stringBuilder.append(c);

            TextView textView = characterTextViews.get(newPos);
            textView.setText(c);
            textView.setTranslationX(getXForTextView(newPos));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(20), 0));
            textView = dotTextViews.get(newPos);
            textView.setTranslationX(getXForTextView(newPos));
            textView.setAlpha(0);
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(20), 0));

            for (int a = newPos + 1; a < 4; a++) {
                textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                }
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
            }
            dotRunnable = new Runnable() {
                @Override
                public void run() {
                    if (dotRunnable != this) {
                        return;
                    }
                    ArrayList<Animator> animators = new ArrayList<>();

                    TextView textView = characterTextViews.get(newPos);
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    textView = dotTextViews.get(newPos);
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 1));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 1));

                    currentAnimation = new AnimatorSet();
                    currentAnimation.setDuration(150);
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (currentAnimation != null && currentAnimation.equals(animation)) {
                                currentAnimation = null;
                            }
                        }
                    });
                    currentAnimation.start();
                }
            };
            AndroidUtilities.runOnUIThread(dotRunnable, 1500);

            for (int a = 0; a < newPos; a++) {
                textView = characterTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
            }

            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(150);
            currentAnimation.playTogether(animators);
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentAnimation != null && currentAnimation.equals(animation)) {
                        currentAnimation = null;
                    }
                }
            });
            currentAnimation.start();

            checkTitle();
        }

        public String getString() {
            return stringBuilder.toString();
        }

        public int length() {
            return stringBuilder.length();
        }

        public boolean eraseLastCharacter() {
            if (stringBuilder.length() == 0) {
                return false;
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception e) {
                FileLog.e(e);
            }

            ArrayList<Animator> animators = new ArrayList<>();
            int deletingPos = stringBuilder.length() - 1;
            if (deletingPos != 0) {
                stringBuilder.deleteCharAt(deletingPos);
            }

            for (int a = deletingPos; a < 4; a++) {
                TextView textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                }
            }

            if (deletingPos == 0) {
                stringBuilder.deleteCharAt(deletingPos);
            }

            for (int a = 0; a < deletingPos; a++) {
                TextView textView = characterTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }

            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(150);
            currentAnimation.playTogether(animators);
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentAnimation != null && currentAnimation.equals(animation)) {
                        currentAnimation = null;
                    }
                }
            });
            currentAnimation.start();

            checkTitle();

            return true;
        }

        private void eraseAllCharacters(final boolean animated) {
            if (stringBuilder.length() == 0) {
                return;
            }
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            stringBuilder.delete(0, stringBuilder.length());
            if (animated) {
                ArrayList<Animator> animators = new ArrayList<>();

                for (int a = 0; a < 4; a++) {
                    TextView textView = characterTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    }

                    textView = dotTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    }
                }

                currentAnimation = new AnimatorSet();
                currentAnimation.setDuration(150);
                currentAnimation.playTogether(animators);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentAnimation != null && currentAnimation.equals(animation)) {
                            currentAnimation = null;
                        }
                    }
                });
                currentAnimation.start();
            } else {
                for (int a = 0; a < 4; a++) {
                    characterTextViews.get(a).setAlpha(0);
                    dotTextViews.get(a).setAlpha(0);
                }
            }

            checkTitle();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }

            for (int a = 0; a < 4; a++) {
                if (a < stringBuilder.length()) {
                    TextView textView = characterTextViews.get(a);
                    textView.setAlpha(0);
                    textView.setScaleX(1);
                    textView.setScaleY(1);
                    textView.setTranslationY(0);
                    textView.setTranslationX(getXForTextView(a));

                    textView = dotTextViews.get(a);
                    textView.setAlpha(1);
                    textView.setScaleX(1);
                    textView.setScaleY(1);
                    textView.setTranslationY(0);
                    textView.setTranslationX(getXForTextView(a));
                } else {
                    characterTextViews.get(a).setAlpha(0);
                    dotTextViews.get(a).setAlpha(0);
                }
            }
            super.onLayout(changed, left, top, right, bottom);
        }
    }

    private void animateBackground(MotionBackgroundDrawable motionBackgroundDrawable) {
        if (backgroundAnimationSpring != null && backgroundAnimationSpring.isRunning()) {
            backgroundAnimationSpring.cancel();
        }

        FloatValueHolder animationValue = new FloatValueHolder(0);
        motionBackgroundDrawable.setAnimationProgressProvider(obj -> animationValue.getValue() / 100f);
        backgroundAnimationSpring = new SpringAnimation(animationValue)
                .setSpring(new SpringForce(100)
                        .setStiffness(BACKGROUND_SPRING_STIFFNESS)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        backgroundAnimationSpring.addEndListener((animation, canceled, value, velocity) -> {
            backgroundAnimationSpring = null;
            motionBackgroundDrawable.setAnimationProgressProvider(null);

            if (!canceled) {
                motionBackgroundDrawable.setPosAnimationProgress(1f);
                if (!backgroundSpringQueue.isEmpty()) {
                    backgroundSpringQueue.poll().run();
                    backgroundSpringNextQueue.poll();
                }
            }
        });
        backgroundAnimationSpring.addUpdateListener((animation, value, velocity) -> motionBackgroundDrawable.updateAnimation(true));
        backgroundAnimationSpring.start();
    }

    private void setNextFocus(View view, @IdRes int nextId) {
        view.setNextFocusForwardId(nextId);
        if (Build.VERSION.SDK_INT >= 22) {
            view.setAccessibilityTraversalBefore(nextId);
        }
    }

    private void checkFingerprintButton() {
        boolean hasFingerprint = false;
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && SharedConfig.useFingerprintLock) {
            try {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints() && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    hasFingerprint = true;
                    fingerprintView.setVisibility(View.VISIBLE);
                } else {
                    fingerprintView.setVisibility(View.GONE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                fingerprintView.setVisibility(View.GONE);
            }
        } else {
            fingerprintView.setVisibility(View.GONE);
        }
        if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            fingerprintImage.setVisibility(fingerprintView.getVisibility());
        }
        subtitleView.setText(LocaleController.getString(hasFingerprint ? R.string.EnterPINorFingerprint : R.string.EnterPIN));
    }

    private void checkTitle() {
        final boolean isEmpty = passwordEditText2 == null || passwordEditText2.length() > 0;
        if (numbersTitleContainer != null) {
            numbersTitleContainer.animate().cancel();
            numbersTitleContainer.animate().alpha(isEmpty ? 0f : 1f).scaleX(isEmpty ? .8f : 1f).scaleY(isEmpty ? .8f : 1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
        }
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType) {
        this.tutorialType = tutorialType;
        showed = false;
        checkFingerprintButton();
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            if (!animated && retryTextView.getVisibility() != View.VISIBLE && passwordEditText != null) {
                passwordEditText.requestFocus();
                AndroidUtilities.showKeyboard(passwordEditText);
            }
        } else {
            if (parentActivity != null) {
                View currentFocus = parentActivity.getCurrentFocus();
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                }
            }
        }
        boolean saturateColors = false;
        backgroundDrawable = null;
        backgroundFrameLayoutColor = 0;
        if (Theme.getCachedWallpaper() instanceof MotionBackgroundDrawable) {
            saturateColors = !Theme.isCurrentThemeDark();
            backgroundDrawable = Theme.getCachedWallpaper();
            backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
        } else if (Theme.isCustomTheme() && !"CJz3BZ6YGEYBAAAABboWp6SAv04".equals(Theme.getSelectedBackgroundSlug()) && !"qeZWES8rGVIEAAAARfWlK1lnfiI".equals(Theme.getSelectedBackgroundSlug())) {
            backgroundDrawable = Theme.getCurrentGradientWallpaper();
            if (backgroundDrawable == null) {
                backgroundDrawable = Theme.getCachedWallpaper();
            }
            if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
            }
        } else {
            String selectedBackgroundSlug = Theme.getSelectedBackgroundSlug();
            if (Theme.DEFAULT_BACKGROUND_SLUG.equals(selectedBackgroundSlug) || Theme.isPatternWallpaper()) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xff517c9e);
            } else {
                backgroundDrawable = Theme.getCachedWallpaper();
                if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
                } else if (backgroundDrawable != null) {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
                } else {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xff517c9e);
                }
            }
        }
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable drawable = (MotionBackgroundDrawable) backgroundDrawable;
            int[] colors = drawable.getColors();
            if (saturateColors) {
                int[] newColors = new int[colors.length];
                for (int i = 0; i < colors.length; ++i) {
                    newColors[i] = Theme.adaptHSV(colors[i], +.14f, 0.0f);
                }
                colors = newColors;
            }
            backgroundDrawable = new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false);
            if (drawable.hasPattern() && drawable.getIntensity() < 0) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x7f000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
            }
            ((MotionBackgroundDrawable) backgroundDrawable).setParentView(backgroundFrameLayout);
        }

        passcodeTextView.setText(LocaleController.getString(R.string.AppLocked));

        if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN) {
            if (retryTextView.getVisibility() != View.VISIBLE) {
                numbersFrameLayout.setVisibility(View.VISIBLE);
            }
            passwordEditText.setVisibility(View.GONE);
            passwordEditText2.setVisibility(View.VISIBLE);
            checkImage.setVisibility(View.GONE);
            fingerprintImage.setVisibility(View.GONE);
        } else if (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            passwordEditText.setFilters(new InputFilter[0]);
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            numbersFrameLayout.setVisibility(View.GONE);
            passwordEditText.setFocusable(true);
            passwordEditText.setFocusableInTouchMode(true);
            passwordEditText.setVisibility(View.VISIBLE);
            passwordEditText2.setVisibility(View.GONE);
            checkImage.setVisibility(View.VISIBLE);
            fingerprintImage.setVisibility(fingerprintView.getVisibility());
        }
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setText("");
        passwordEditText2.eraseAllCharacters(false);
        showed = true;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);

        FrameLayout.LayoutParams layoutParams;

        int sizeBetweenNumbersX = dp(BUTTON_X_MARGIN);
        int sizeBetweenNumbersY = dp(BUTTON_Y_MARGIN);
        int buttonSize = dp(BUTTON_SIZE);

        final boolean landscape = !AndroidUtilities.isTablet() && context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (border != null) {
            border.setVisibility(SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PASSWORD ? View.VISIBLE : View.GONE);
        }

        if (landscape) {
            layoutParams = (FrameLayout.LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.width = SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN ? width / 2 : width;
            layoutParams.height = dp(180);
            layoutParams.topMargin = (height - dp(140)) / 2 + (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN ? dp(40) : 0);
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) numbersContainer.getLayoutParams();
            layoutParams.height = height;
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = height - layoutParams.height + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            layoutParams.width = width / 2;
            numbersContainer.setLayoutParams(layoutParams);

            int cols = 3;
            int rows = 4;
            layoutParams = (FrameLayout.LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + buttonSize * rows + sizeBetweenNumbersY * Math.max(0, rows - 1);
            layoutParams.width =  buttonSize * cols + sizeBetweenNumbersX * Math.max(0, cols - 1);
            layoutParams.gravity = Gravity.CENTER;
            numbersFrameLayout.setLayoutParams(layoutParams);
        } else {
            int top = AndroidUtilities.statusBarHeight;
            int left = 0;
            if (AndroidUtilities.isTablet()) {
                if (width > dp(498)) {
                    left = (width - dp(498)) / 2;
                    width = dp(498);
                }
                if (height > dp(528)) {
                    top = (height - dp(528)) / 2;
                    height = dp(528);
                }
            }
            layoutParams = (FrameLayout.LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.height = height / 3 + (SharedConfig.getPasscodeType() == SharedConfig.PASSCODE_TYPE_PIN ? dp(40) : 0);
            layoutParams.width = width;
            layoutParams.topMargin = top;
            layoutParams.leftMargin = left;
            passwordFrameLayout.setTag(top);
            passwordFrameLayout.setLayoutParams(layoutParams);
            int passwordTop = layoutParams.topMargin + layoutParams.height;

            int cols = 3;
            int rows = 4;
            layoutParams = (FrameLayout.LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + buttonSize * rows + sizeBetweenNumbersY * Math.max(0, rows - 1);
            layoutParams.width =  buttonSize * cols + sizeBetweenNumbersX * Math.max(0, cols - 1);
            if (AndroidUtilities.isTablet()) {
                layoutParams.gravity = Gravity.CENTER;
            } else {
                layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            }
            numbersFrameLayout.setLayoutParams(layoutParams);

            int buttonHeight = height - layoutParams.height;
            layoutParams = (FrameLayout.LayoutParams) numbersContainer.getLayoutParams();
            layoutParams.leftMargin = left;
            if (AndroidUtilities.isTablet()) {
                layoutParams.topMargin = (height - buttonHeight) / 2;
            } else {
                layoutParams.topMargin = passwordTop;
            }
            layoutParams.width = width;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            numbersContainer.setLayoutParams(layoutParams);
        }

        int headerMargin = dp(landscape ? 52 : 82);
        for (int a = 0; a < 12; a++) {
            FrameLayout.LayoutParams layoutParams1;
            int num;
            if (a == 0) {
                num = 10;
            } else if (a == 10) {
                num = 11;
            } else if (a == 11) {
                num = 9;
            } else {
                num = a - 1;
            }
            int row = num / 3;
            int col = num % 3;
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            layoutParams1 = (FrameLayout.LayoutParams) frameLayout.getLayoutParams();
            layoutParams1.topMargin = headerMargin + (buttonSize + sizeBetweenNumbersY) * row;
            layoutParams1.leftMargin = (buttonSize + sizeBetweenNumbersX) * col;
            frameLayout.setLayoutParams(layoutParams1);
        }
    }

    @Override
    public void onPasscodeError() {
        passwordEditText.setText("");
        passwordEditText2.eraseAllCharacters(true);
        BotWebViewVibrationEffect.NOTIFICATION_ERROR.vibrate();
        shakeTextView(2, 0);
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
            if (backgroundAnimationSpring != null) {
                backgroundAnimationSpring.cancel();
                motionBackgroundDrawable.setPosAnimationProgress(1f);
            }
            if (motionBackgroundDrawable.getPosAnimationProgress() >= 1f) {
                motionBackgroundDrawable.rotatePreview(true);
            }
        }
        BotWebViewVibrationEffect.NOTIFICATION_ERROR.vibrate();
        shakeTextView(2, 0);
    }

    private int shiftDp = -12;
    private void shakeTextView(final float x, final int num) {
        if (num == 6) {
            return;
        }
        AndroidUtilities.shakeViewSpring(numbersTitleContainer, shiftDp = -shiftDp);
    }
}
