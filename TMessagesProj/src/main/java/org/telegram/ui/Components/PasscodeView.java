package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.ui.LaunchActivity;

public class PasscodeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private boolean showed = false;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.passcodeDismissed) {
            if (args[0] != this) {
                setVisibility(GONE);
            }
        } else if (id == NotificationCenter.fakePasscodeActivated) {
            if (FakePasscodeUtils.isFakePasscodeActivated() && !FakePasscodeUtils.getActivatedFakePasscode().passcodeEnabled()) {
                appUnlocked();
            }
        }
    }

    public interface PasscodeViewDelegate {
        void didAcceptedPassword(PasscodeView view);
    }

    private PasscodeViewDelegate delegate;

    AbstractMaskedPasscodeScreen screen;

    public PasscodeView(final Context context) {
        super(context);


        setWillNotDraw(false);
        setVisibility(GONE);

        screen = MaskedPtgConfig.createScreen(context, password -> processDone(false, password), true);
        addView(screen.createView());
        if (screen != null) {
            return;
        }

        setWillNotDraw(false);
        setVisibility(GONE);
    }

    public void setDelegate(PasscodeViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void processDone(boolean fingerprint, String password) {
        if (!fingerprint) {
            if (password.length() == 0 && SharedConfig.passcodeEnabled()) {
                if (screen != null) {
                    screen.onPasscodeError();
                }
                return;
            }
            SharedConfig.PasscodeCheckResult result;
            if (SharedConfig.passcodeRetryInMs > 0 && SharedConfig.passcodeEnabled()) {
                result = SharedConfig.PasscodeCheckResult.createFailedResult();
            } else {
                result = SharedConfig.checkPasscode(password);
            }
            synchronized (FakePasscode.class) {
                if (SharedConfig.fakePasscodeActivatedIndex != SharedConfig.fakePasscodes.indexOf(result.fakePasscode)) {
                    result.activateFakePasscode();
                    SharedConfig.saveConfig();
                }
                if (!result.allowLogin() || result.fakePasscode != null && !result.fakePasscode.replaceOriginalPasscode
                        || SharedConfig.bruteForceProtectionEnabled && SharedConfig.bruteForceRetryInMillis > 0) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.AppUnlockType, result.fakePasscode != null);
                    SharedConfig.addBadPasscodeAttempt(badAttempt);
                    badAttempt.takePhotos(getContext());
                }
            }
            if (!result.allowLogin() || SharedConfig.bruteForceProtectionEnabled && SharedConfig.bruteForceRetryInMillis > 0) {
                if (SharedConfig.passcodeRetryInMs <= 0) {
                    SharedConfig.increaseBadPasscodeTries();
                }
                if (SharedConfig.passcodeRetryInMs > 0) {
                    checkRetryTextView();
                }
                if (screen != null) {
                    screen.onPasscodeError();
                }
                return;
            }
        } else {
            FakePasscode fakePasscode = FakePasscodeUtils.getFingerprintFakePasscode();
            synchronized (FakePasscode.class) {
                if (fakePasscode != null) {
                    fakePasscode.executeActions();
                } else if (FakePasscodeUtils.isFakePasscodeActivated()) {
                    FakePasscodeUtils.getActivatedFakePasscode().deactivate();
                }
                SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(fakePasscode));
                SharedConfig.saveConfig();
                if (fakePasscode != null && !fakePasscode.replaceOriginalPasscode) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.AppUnlockType, true);
                    SharedConfig.addBadPasscodeAttempt(badAttempt);
                    badAttempt.takePhotos(getContext());
                }
            }
            if (fakePasscode != null && !fakePasscode.allowLogin) {
                SharedConfig.increaseBadPasscodeTries();
                if (SharedConfig.passcodeRetryInMs > 0) {
                    checkRetryTextView();
                }
                return;
            }
        }
        SharedConfig.badPasscodeTries = 0;
        SharedConfig.needShowMaskedPasscodeScreenTutorial = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && FingerprintController.isKeyReady() && FingerprintController.checkDeviceFingerprintsChanged()) {
            FingerprintController.deleteInvalidKey();
        }

        appUnlocked();
    }

    private void appUnlocked() {
        SharedConfig.setAppLocked(false);
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        setOnTouchListener(null);
        if (delegate != null) {
            delegate.didAcceptedPassword(this);
        }

        AndroidUtilities.runOnUIThread(this::hidePasscodeView);
    }

    private void hidePasscodeView() {
        if (!showed) {
            AndroidUtilities.runOnUIThread(this::hidePasscodeView, 500);
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(shownT, 0);
        va.addUpdateListener(anm -> {
            shownT = (float) anm.getAnimatedValue();
            onAnimationUpdate(shownT);
            setAlpha(shownT);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.GONE);
                onHidden();
                onAnimationUpdate(shownT = 0f);
                setAlpha(0f);
            }
        });
        va.setDuration(420);
        va.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        va.start();
    }

    private float shownT;
    protected void onAnimationUpdate(float open) {

    }

    protected void onHidden() {

    }

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkRetryTextView();
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        }
    };

    private void checkRetryTextView() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime > SharedConfig.lastUptimeMillis) {
            SharedConfig.passcodeRetryInMs -= (currentTime - SharedConfig.lastUptimeMillis);
            if (SharedConfig.passcodeRetryInMs < 0) {
                SharedConfig.passcodeRetryInMs = 0;
            }
            SharedConfig.bruteForceRetryInMillis -= (currentTime - SharedConfig.lastUptimeMillis);
            if (SharedConfig.bruteForceRetryInMillis < 0) {
                SharedConfig.bruteForceRetryInMillis = 0;
            }
        }
        SharedConfig.lastUptimeMillis = currentTime;
        SharedConfig.saveConfig();
        if (SharedConfig.passcodeRetryInMs > 0) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        } else {
            if (!SharedConfig.bruteForceProtectionEnabled || SharedConfig.bruteForceRetryInMillis <= 0) {
                AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            }
        }
    }

    public void onResume() {
        checkRetryTextView();
    }

    public boolean onBackPressed() {
        return screen.onBackPressed();
    }

    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(checkRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (screen != null) {
            screen.onAttachedToWindow();
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.fakePasscodeActivated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (screen != null) {
            screen.onDetachedFromWindow();
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.fakePasscodeActivated);
    }

    public void onShow(boolean fingerprint, boolean animated) {
        onShow(fingerprint, animated, -1, -1, null, null);
    }

    public void onShow(boolean fingerprint, boolean animated, int x, int y, Runnable onShow, Runnable onStart) {
        Context context = getContext();
        if (context instanceof LaunchActivity) {
            ((LaunchActivity)context).invalidateFlagSecureReason();
        }
        checkRetryTextView();
        Activity parentActivity = AndroidUtilities.findActivity(getContext());
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {

        } else {
            if (parentActivity != null) {
                View currentFocus = parentActivity.getCurrentFocus();
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                    AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                }
            }
        }
        if (getVisibility() == View.VISIBLE) {
            return;
        }
        setTranslationY(0);
        setVisibility(View.VISIBLE);
        if (screen != null) {
            TutorialType tutorialType = SharedConfig.needShowMaskedPasscodeScreenTutorial
                    ? TutorialType.FULL
                    : TutorialType.DISABLED;
            screen.onShow(fingerprint, animated, tutorialType);
        }
        checkRetryTextView();
        if (animated) {
            setAlpha(0.0f);
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    setAlpha(1.0f);
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    AndroidUtilities.runOnUIThread(() -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING), 350);
                }
            });
            requestLayout();
        } else {
            setAlpha(1.0f);
            onAnimationUpdate(shownT = 1f);
            if (onShow != null) {
                onShow.run();
            }
        }

        setOnTouchListener((v, event) -> true);
        showed = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (screen != null) {
            screen.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
