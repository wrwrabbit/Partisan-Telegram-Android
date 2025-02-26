/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.appmigration.MaskedMigrationIssue;
import org.telegram.messenger.partisan.appmigration.MaskedMigratorHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import java.util.concurrent.atomic.AtomicBoolean;

public class PasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public final static int TYPE_MANAGE_CODE_SETTINGS = 0,
            TYPE_SETUP_CODE = 1,
            TYPE_ENTER_CODE_TO_MANAGE_SETTINGS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TYPE_MANAGE_CODE_SETTINGS,
            TYPE_SETUP_CODE,
            TYPE_ENTER_CODE_TO_MANAGE_SETTINGS
    })
    public @interface PasscodeActivityType {}

    private enum ErrorType {
        PASSCODES_DO_NOT_MATCH,
        PASSCODE_IN_USE
    }

    private final static int ID_SWITCH_TYPE = 1;

    private RLottieImageView lockImageView;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextViewSwitcher descriptionTextSwitcher;
    private OutlineTextContainerView outlinePasswordView;
    private EditTextBoldCursor passwordEditText;
    private CodeFieldContainer codeFieldContainer;
    private TextView passcodesErrorTextView;

    private ImageView passwordButton;

    private CustomPhoneKeyboardView keyboardView;

    private FrameLayout floatingButtonContainer;
    private VerticalPositionAutoAnimator floatingAutoAnimator;
    private TransformableLoginButtonView floatingButtonIcon;
    private Animator floatingButtonAnimator;

    @PasscodeActivityType
    private int type;
    @SharedConfig.PasscodeType
    private int currentPasswordType = 0;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int utyanRow;
    private int hintRow;

    private int changePasscodeRow;
    private int fingerprintRow;
    private int autoLockRow;
    private int autoLockDetailRow;

    private int fakePasscodesHeaderRow;
    private int firstFakePasscodeRow;
    private int lastFakePasscodeRow;
    private int addFakePasscodeRow;
    private int restoreFakePasscodeDelimiterRow;
    private int restoreFakePasscodeRow;
    private int fakePasscodeDetailRow;

    private int captureHeaderRow;
    private int captureRow;
    private int captureDetailRow;

    private int bruteForceProtectionRow;
    private int bruteForceProtectionDetailRow;

    private int clearCacheOnLockRow;
    private int clearCacheOnLockDetailRow;

    private int badPasscodeAttemptsRow;
    private int badPasscodePhotoFrontRow;
    private int badPasscodePhotoBackRow;
    private int badPasscodeMuteAudioRow;
    private int badPasscodeAttemptsDetailRow;

    private int partisanSettingsRow;
    private int partisanSettingsDetailRow;

    private int disablePasscodeRow;
    private int rowCount;

    TextCheckCell frontPhotoTextCell;
    TextCheckCell backPhotoTextCell;

    private ActionBarMenuItem otherItem;

    private boolean postedHidePasscodesDoNotMatch;
    private Runnable hidePasscodesDoNotMatch = () -> {
        postedHidePasscodesDoNotMatch = false;
        AndroidUtilities.updateViewVisibilityAnimated(passcodesErrorTextView, false);
    };

    private Runnable onShowKeyboardCallback;

    public PasscodeActivity(@PasscodeActivityType int type) {
        super();
        this.type = type;
        if (type == TYPE_SETUP_CODE && !SharedConfig.fakePasscodes.isEmpty()) {
            currentPasswordType = SharedConfig.passcodeType;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        View fragmentContentView;
        FrameLayout frameLayout = new FrameLayout(context);
        if (type == TYPE_MANAGE_CODE_SETTINGS) {
            fragmentContentView = frameLayout;
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            scrollView.setFillViewport(true);
            fragmentContentView = scrollView;
        }
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int frameBottom;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() >= AndroidUtilities.dp(20)) {
                    if (isCustomKeyboardVisible()) {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) + measureKeyboardHeight());
                    } else {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                    }
                } else if (keyboardView.getVisibility() != View.GONE) {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                } else {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                }

                keyboardView.layout(0, frameBottom, getMeasuredWidth(), frameBottom + AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                notifyHeightChanged();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);

                int frameHeight = height;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() < AndroidUtilities.dp(20)) {
                    frameHeight -= AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                }
                fragmentContentView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
                keyboardView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP), MeasureSpec.EXACTLY));
            }
        };
        contentView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight >= AndroidUtilities.dp(20) && onShowKeyboardCallback != null) {
                onShowKeyboardCallback.run();
                onShowKeyboardCallback = null;
            }
        });
        fragmentView = contentView;
        contentView.addView(fragmentContentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setVisibility(isCustomKeyboardVisible() ? View.VISIBLE : View.GONE);
        contentView.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        switch (type) {
            case TYPE_MANAGE_CODE_SETTINGS: {
                actionBar.setTitle(LocaleController.getString(R.string.Passcode));
                frameLayout.setTag(Theme.key_windowBackgroundGray);
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                listView = new RecyclerListView(context);
                listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                    @Override
                    public boolean supportsPredictiveItemAnimations() {
                        return false;
                    }
                });
                listView.setVerticalScrollBarEnabled(false);
                listView.setItemAnimator(null);
                listView.setLayoutAnimation(null);
                frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                listView.setAdapter(listAdapter = new ListAdapter(context));
                listView.setOnItemClickListener((view, position) -> {
                    if (!view.isEnabled()) {
                        return;
                    }
                    if (position == disablePasscodeRow) {
                        String message = LocaleController.getString(R.string.DisablePasscodeConfirmMessage);
                        if (FakePasscodeUtils.getActivatedFakePasscode() == null && !SharedConfig.fakePasscodes.isEmpty()) {
                            message += " " + LocaleController.getString("AllFakePasscodesWillBeDeleted", R.string.AllFakePasscodesWillBeDeleted);
                        }
                        AlertDialog alertDialog = new AlertDialog.Builder(getParentActivity())
                                .setTitle(LocaleController.getString(R.string.DisablePasscode))
                                .setMessage(message)
                                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                .setPositiveButton(LocaleController.getString(R.string.DisablePasscodeTurnOff), (dialog, which) -> {
                                    if (FakePasscodeUtils.getActivatedFakePasscode() != null) {
                                        FakePasscodeUtils.getActivatedFakePasscode().passwordDisabled = true;
                                    } else {
                                        SharedConfig.setPasscode("");
                                        for (FakePasscode passcode: SharedConfig.fakePasscodes) {
                                            passcode.onDelete();
                                        }
                                        SharedConfig.fakePasscodes.clear();
                                        MaskedMigratorHelper.removeMigrationIssueAndShowDialogIfNeeded(this, MaskedMigrationIssue.INVALID_PASSCODE_TYPE);
                                        MaskedMigratorHelper.removeMigrationIssueAndShowDialogIfNeeded(this, MaskedMigrationIssue.PASSWORDLESS_MODE);
                                        MaskedMigratorHelper.removeMigrationIssueAndShowDialogIfNeeded(this, MaskedMigrationIssue.ACTIVATE_BY_FINGERPRINT);
                                    }
                                    SharedConfig.setAppLocked(false);
                                    SharedConfig.saveConfig();
                                    getMediaDataController().buildShortcuts();
                                    int count = listView.getChildCount();
                                    for (int a = 0; a < count; a++) {
                                        View child = listView.getChildAt(a);
                                        if (child instanceof TextSettingsCell) {
                                            TextSettingsCell textCell = (TextSettingsCell) child;
                                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                                            break;
                                        }
                                    }
                                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
                                    finishFragment();
                                }).create();
                        alertDialog.show();
                        ((TextView)alertDialog.getButton(Dialog.BUTTON_POSITIVE)).setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    } else if (position == changePasscodeRow) {
                        presentFragment(new PasscodeActivity(TYPE_SETUP_CODE));
                    } else if (position == autoLockRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString(R.string.AutoLock));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        numberPicker.setMinValue(0);
                        int fakePasscodeValueShift = FakePasscodeUtils.getActivatedFakePasscode() == null ? 0 : 1;
                        numberPicker.setMaxValue(5 - fakePasscodeValueShift);
                        if (SharedConfig.getAutoLockIn() == 0) {
                            numberPicker.setValue(0);
                        } else if (SharedConfig.getAutoLockIn() == 1) {
                            numberPicker.setValue(1);
                        } else if (SharedConfig.getAutoLockIn() == 60) {
                            numberPicker.setValue(2 - fakePasscodeValueShift);
                        } else if (SharedConfig.getAutoLockIn() == 60 * 5) {
                            numberPicker.setValue(3 - fakePasscodeValueShift);
                        } else if (SharedConfig.getAutoLockIn() == 60 * 60) {
                            numberPicker.setValue(4 - fakePasscodeValueShift);
                        } else if (SharedConfig.getAutoLockIn() == 60 * 60 * 5) {
                            numberPicker.setValue(5 - fakePasscodeValueShift);
                        }
                        numberPicker.setFormatter(value -> {
                            if (value == 0) {
                                return LocaleController.getString(R.string.AutoLockDisabled);
                            } else if (fakePasscodeValueShift == 0 && value == 1) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Seconds", 1));
                            } else if (value == 2 - fakePasscodeValueShift) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 1));
                            } else if (value == 3 - fakePasscodeValueShift) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 5));
                            } else if (value == 4 - fakePasscodeValueShift) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 1));
                            } else if (value == 5 - fakePasscodeValueShift) {
                                return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 5));
                            }
                            return "";
                        });
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
                            which = numberPicker.getValue();
                            if (which == 0) {
                                SharedConfig.autoLockIn = 0;
                            } else if (fakePasscodeValueShift == 0 && which == 1) {
                                SharedConfig.autoLockIn = 1;
                            } else if (which == 2 - fakePasscodeValueShift) {
                                SharedConfig.autoLockIn = 60;
                            } else if (which == 3 - fakePasscodeValueShift) {
                                SharedConfig.autoLockIn = 60 * 5;
                            } else if (which == 4 - fakePasscodeValueShift) {
                                SharedConfig.autoLockIn = 60 * 60;
                            } else if (which == 5 - fakePasscodeValueShift) {
                                SharedConfig.autoLockIn = 60 * 60 * 5;
                            }
                            listAdapter.notifyItemChanged(position);
                            UserConfig.getInstance(currentAccount).saveConfig(false);
                        });
                        showDialog(builder.create());
                    } else if (position == bruteForceProtectionRow) {
                        SharedConfig.bruteForceProtectionEnabled = !SharedConfig.bruteForceProtectionEnabled;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.bruteForceProtectionEnabled);
                    } else if (position == clearCacheOnLockRow) {
                        SharedConfig.clearCacheOnLock = !SharedConfig.clearCacheOnLock;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.clearCacheOnLock);
                    } else if (position == badPasscodeAttemptsRow) {
                        presentFragment(new BadPasscodeAttemptsActivity());
                    } else if (position == badPasscodePhotoFrontRow) {
                        showPhotoWarning(() -> {
                            Activity parentActivity = getParentActivity();
                            if (SharedConfig.takePhotoWithBadPasscodeFront || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                                SharedConfig.saveConfig();
                                ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                            } else {
                                ActivityCompat.requestPermissions(parentActivity, new String[]{Manifest.permission.CAMERA}, 2000);
                            }
                            updateRows();
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        });
                    } else if (position == badPasscodePhotoBackRow) {
                        showPhotoWarning(() -> {
                            Activity parentActivity = getParentActivity();
                            if (SharedConfig.takePhotoWithBadPasscodeBack || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                                SharedConfig.saveConfig();
                                ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                            } else {
                                ActivityCompat.requestPermissions(parentActivity, new String[]{Manifest.permission.CAMERA}, 2001);
                            }
                            updateRows();
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        });
                    } else if (position == badPasscodeMuteAudioRow) {
                        SharedConfig.takePhotoMuteAudio = !SharedConfig.takePhotoMuteAudio;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.takePhotoMuteAudio);
                    } else if (position == fingerprintRow) {
                        SharedConfig.useFingerprintLock = !SharedConfig.useFingerprintLock;
                        if (FakePasscodeUtils.isFakePasscodeActivated()) {
                            FakePasscodeUtils.getActivatedFakePasscode().activateByFingerprint = SharedConfig.useFingerprintLock;
                        } else if (!SharedConfig.useFingerprintLock) {
                            for (FakePasscode fakePasscode : SharedConfig.fakePasscodes) {
                                fakePasscode.activateByFingerprint = false;
                            }
                        }
                        if (!SharedConfig.useFingerprintLock) {
                            MaskedMigratorHelper.removeMigrationIssueAndShowDialogIfNeeded(this, MaskedMigrationIssue.ACTIVATE_BY_FINGERPRINT);
                        }
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                        ((TextCheckCell) view).setChecked(SharedConfig.useFingerprintLock);
                    } else if (position == captureRow) {
                        SharedConfig.allowScreenCapture = !SharedConfig.allowScreenCapture;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                        ((TextCheckCell) view).setChecked(SharedConfig.allowScreenCapture);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode, false);
                        if (!SharedConfig.allowScreenCapture) {
                            AlertsCreator.showSimpleAlert(PasscodeActivity.this, LocaleController.getString(R.string.ScreenCaptureAlert));
                        }
                    } else if (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                        presentFragment(new FakePasscodeActivity(FakePasscodeActivity.TYPE_FAKE_PASSCODE_SETTINGS, SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow), false));
                    } else if (position == addFakePasscodeRow) {
                        FakePasscode fakePasscode = FakePasscode.create();
                        presentFragment(new FakePasscodeActivity(FakePasscodeActivity.TYPE_SETUP_FAKE_PASSCODE, fakePasscode, true));
                    } else if (position == restoreFakePasscodeRow) {
                        presentFragment(new FakePasscodeRestoreActivity());
                    } else if (position == partisanSettingsRow) {
                        presentFragment(new PartisanSettingsActivity());
                    }
                });
                break;
            }
            case TYPE_SETUP_CODE:
            case TYPE_ENTER_CODE_TO_MANAGE_SETTINGS: {
                if (actionBar != null) {
                    actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                    actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
                    actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
                    actionBar.setCastShadows(false);
                    ActionBarMenu menu = actionBar.createMenu();

                    ActionBarMenuSubItem switchItem;
                    if (type == TYPE_SETUP_CODE) {
                        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                        switchItem = otherItem.addSubItem(ID_SWITCH_TYPE, R.drawable.msg_permissions, LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.PasscodeSwitchToPassword : R.string.PasscodeSwitchToPIN));
                    } else switchItem = null;

                    actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                        @Override
                        public void onItemClick(int id) {
                            if (id == -1) {
                                finishFragment();
                            } else if (id == ID_SWITCH_TYPE) {
                                currentPasswordType = currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? SharedConfig.PASSCODE_TYPE_PASSWORD : SharedConfig.PASSCODE_TYPE_PIN;
                                if (!SharedConfig.fakePasscodes.isEmpty() && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD && currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN) {
                                    showPasswordToPinSwitchWarning();
                                }
                                AndroidUtilities.runOnUIThread(()->{
                                    switchItem.setText(LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.PasscodeSwitchToPassword : R.string.PasscodeSwitchToPIN));
                                    switchItem.setIcon(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.drawable.msg_permissions : R.drawable.msg_pin_code);
                                    showKeyboard();
                                    if (isPinCode()) {
                                        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                                        AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, false);
                                    }
                                }, 150);
                                passwordEditText.setText("");
                                for (CodeNumberField f : codeFieldContainer.codeField) {
                                    f.setText("");
                                }
                                updateFields();
                            }
                        }
                    });
                }

                FrameLayout codeContainer = new FrameLayout(context);

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

                titleTextView = new TextView(context);
                titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                titleTextView.setTypeface(AndroidUtilities.bold());
                if (type == TYPE_SETUP_CODE) {
                    if (SharedConfig.passcodeEnabled()) {
                        titleTextView.setText(LocaleController.getString(R.string.EnterNewPasscode));
                    } else {
                        titleTextView.setText(LocaleController.getString(R.string.CreatePasscode));
                    }
                } else {
                    titleTextView.setText(LocaleController.getString(R.string.EnterYourPasscode));
                }
                titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

                descriptionTextSwitcher = new TextViewSwitcher(context);
                descriptionTextSwitcher.setFactory(() -> {
                    TextView tv = new TextView(context);
                    tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    tv.setGravity(Gravity.CENTER_HORIZONTAL);
                    tv.setLineSpacing(AndroidUtilities.dp(2), 1);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    return tv;
                });
                descriptionTextSwitcher.setInAnimation(context, R.anim.alpha_in);
                descriptionTextSwitcher.setOutAnimation(context, R.anim.alpha_out);
                innerLinearLayout.addView(descriptionTextSwitcher, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 8, 20, 0));

                TextView forgotPasswordButton = new TextView(context);
                forgotPasswordButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                forgotPasswordButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                forgotPasswordButton.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
                forgotPasswordButton.setGravity((isPassword() ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL) | Gravity.CENTER_VERTICAL);

                forgotPasswordButton.setOnClickListener(v -> AlertsCreator.createForgotPasscodeDialog(context).show());
                forgotPasswordButton.setVisibility(type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS ? View.VISIBLE : View.GONE);
                forgotPasswordButton.setText(LocaleController.getString(R.string.ForgotPasscode));
                frameLayout.addView(forgotPasswordButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 56 : 60, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));
                VerticalPositionAutoAnimator.attach(forgotPasswordButton);

                passcodesErrorTextView = new TextView(context);
                passcodesErrorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                passcodesErrorTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                passcodesErrorTextView.setText(LocaleController.getString(R.string.PasscodesDoNotMatchTryAgain));
                passcodesErrorTextView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
                AndroidUtilities.updateViewVisibilityAnimated(passcodesErrorTextView, false, 1f, false);
                frameLayout.addView(passcodesErrorTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

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
                if (type == TYPE_SETUP_CODE) {
                    passcodeSetStep = 0;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                } else {
                    passcodeSetStep = 1;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                }
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
                passwordEditText.setCursorSize(AndroidUtilities.dp(20));
                passwordEditText.setCursorWidth(1.5f);

                int padding = AndroidUtilities.dp(16);
                passwordEditText.setPadding(padding, padding, padding, padding);

                passwordEditText.setOnFocusChangeListener((v, hasFocus) -> outlinePasswordView.animateSelection(hasFocus ? 1 : 0));

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setGravity(Gravity.CENTER_VERTICAL);
                linearLayout.addView(passwordEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                passwordButton = new ImageView(context);
                passwordButton.setImageResource(R.drawable.msg_message);
                passwordButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passwordButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1));
                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, type == TYPE_SETUP_CODE && passcodeSetStep == 0, 0.1f, false);

                AtomicBoolean isPasswordShown = new AtomicBoolean(false);
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (type == TYPE_SETUP_CODE && passcodeSetStep == 0) {
                            if (TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.GONE) {
                                if (isPasswordShown.get()) {
                                    passwordButton.callOnClick();
                                }
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, false, 0.1f, true);
                            } else if (!TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.VISIBLE) {
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, true);
                            }
                        }
                    }
                });

                passwordButton.setOnClickListener(v -> {
                    isPasswordShown.set(!isPasswordShown.get());

                    int selectionStart = passwordEditText.getSelectionStart(), selectionEnd = passwordEditText.getSelectionEnd();
                    passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | (isPasswordShown.get() ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                    passwordEditText.setSelection(selectionStart, selectionEnd);

                    passwordButton.setColorFilter(Theme.getColor(isPasswordShown.get() ? Theme.key_windowBackgroundWhiteInputFieldActivated : Theme.key_windowBackgroundWhiteHintText));
                });
                linearLayout.addView(passwordButton, LayoutHelper.createLinearRelatively(24, 24, 0, 0, 0, 14, 0));

                outlinePasswordView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                codeContainer.addView(outlinePasswordView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 0));

                passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (passcodeSetStep == 0) {
                        processNext();
                        return true;
                    } else if (passcodeSetStep == 1) {
                        processDone();
                        return true;
                    }
                    return false;
                });
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (postedHidePasscodesDoNotMatch) {
                            codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                            hidePasscodesDoNotMatch.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
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

                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        if (passcodeSetStep == 0) {
                            postDelayed(()->processNext(), 260);
                        } else {
                            processDone();
                        }
                    }
                };
                codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
                    f.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
                    f.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            if (postedHidePasscodesDoNotMatch) {
                                codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                                hidePasscodesDoNotMatch.run();
                            }
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                    f.setOnFocusChangeListener((v, hasFocus) -> {
                        keyboardView.setEditText(f);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    });
                }
                codeContainer.addView(codeFieldContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 10, 40, 0));

                innerLinearLayout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 72));

                if (type == TYPE_SETUP_CODE) {
                    frameLayout.setTag(Theme.key_windowBackgroundWhite);
                }

                floatingButtonContainer = new FrameLayout(context);
                if (Build.VERSION.SDK_INT >= 21) {
                    StateListAnimator animator = new StateListAnimator();
                    animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                    animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                    floatingButtonContainer.setStateListAnimator(animator);
                    floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                        @SuppressLint("NewApi")
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                        }
                    });
                }
                floatingAutoAnimator = VerticalPositionAutoAnimator.attach(floatingButtonContainer);
                frameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 24, 16));
                floatingButtonContainer.setOnClickListener(view -> {
                    if (type == TYPE_SETUP_CODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
                        processDone();
                    }
                });

                floatingButtonIcon = new TransformableLoginButtonView(context);
                floatingButtonIcon.setTransformType(TransformableLoginButtonView.TRANSFORM_ARROW_CHECK);
                floatingButtonIcon.setProgress(0f);
                floatingButtonIcon.setColor(Theme.getColor(Theme.key_chats_actionIcon));
                floatingButtonIcon.setDrawBackground(false);
                floatingButtonContainer.setContentDescription(LocaleController.getString(R.string.Next));
                floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

                Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                    shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                    combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    drawable = combinedDrawable;
                }
                floatingButtonContainer.setBackground(drawable);

                updateFields();
                break;
            }
        }

        return fragmentView;
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return type != TYPE_MANAGE_CODE_SETTINGS;
    }

    /**
     * Sets custom keyboard visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
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
            keyboardView.setTranslationY(visible ? 0 : AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
            if (FakePasscodeUtils.isFakePasscodeActivated() || fragmentView != null) {
                fragmentView.requestLayout();
            }
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? CubicBezierInterpolator.DEFAULT : Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                keyboardView.setAlpha(val);
                keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) * 0.75f);
                fragmentView.requestLayout();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        keyboardView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        keyboardView.setVisibility(View.GONE);
                    }
                }
            });
            animator.start();
        }
    }

    /**
     * Sets floating button visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
    private void setFloatingButtonVisible(boolean visible, boolean animate) {
        if (floatingButtonAnimator != null) {
            floatingButtonAnimator.cancel();
            floatingButtonAnimator = null;
        }
        if (!animate) {
            floatingAutoAnimator.setOffsetY(visible ? 0 : AndroidUtilities.dp(70));
            floatingButtonContainer.setAlpha(visible ? 1f : 0f);
            floatingButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? AndroidUtilities.decelerateInterpolator : AndroidUtilities.accelerateInterpolator);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                floatingAutoAnimator.setOffsetY(AndroidUtilities.dp(70) * (1f - val));
                floatingButtonContainer.setAlpha(val);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        floatingButtonContainer.setVisibility(View.GONE);
                    }
                    if (floatingButtonAnimator == animation) {
                        floatingButtonAnimator = null;
                    }
                }
            });
            animator.start();
            floatingButtonAnimator = animator;
        }
    }

    /**
     * @return New fragment to open when Passcode entry gets clicked
     */
    public static BaseFragment determineOpenFragment() {
        if (SharedConfig.passcodeEnabled()) {
            return new PasscodeActivity(TYPE_ENTER_CODE_TO_MANAGE_SETTINGS);
        }
        return new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_SET_PASSCODE);
    }

    private void animateSuccessAnimation(Runnable callback) {
        if (!isPinCode()) {
            callback.run();
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            CodeNumberField field = codeFieldContainer.codeField[i];
            field.postDelayed(()-> field.animateSuccessProgress(1f), i * 75L);
        }
        codeFieldContainer.postDelayed(() -> {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateSuccessProgress(0f);
            }
            callback.run();
        }, codeFieldContainer.codeField.length * 75L + 350L);
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

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if ((requestCode == 2000 || requestCode == 2001) && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (requestCode == 2000) {
                        SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                        SharedConfig.saveConfig();
                        frontPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                    } else {
                        SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                        SharedConfig.saveConfig();
                        backPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                });
            } else {
                new AlertDialog.Builder(getParentActivity())
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
        }

        if (requestCode == 2000 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

        } else if (requestCode == 2001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                SharedConfig.saveConfig();
                backPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != TYPE_MANAGE_CODE_SETTINGS && !isCustomKeyboardVisible()) {
            AndroidUtilities.runOnUIThread(this::showKeyboard, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);

        if (isCustomKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        }
        updateRows();
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode && (args.length == 0 || (Boolean) args[0])) {
            if (type == TYPE_MANAGE_CODE_SETTINGS) {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateRows() {
        fingerprintRow = -1;
        rowCount = 0;

        fakePasscodesHeaderRow = -1;
        firstFakePasscodeRow = -1;
        lastFakePasscodeRow = -1;
        addFakePasscodeRow = -1;
        restoreFakePasscodeDelimiterRow = -1;
        restoreFakePasscodeRow = -1;
        fakePasscodeDetailRow = -1;
        bruteForceProtectionRow = -1;
        clearCacheOnLockRow = -1;
        clearCacheOnLockDetailRow = -1;
        bruteForceProtectionDetailRow = -1;
        badPasscodeAttemptsRow = -1;
        badPasscodePhotoFrontRow = -1;
        badPasscodePhotoBackRow = -1;
        badPasscodeMuteAudioRow = -1;
        badPasscodeAttemptsDetailRow = -1;
        partisanSettingsRow = -1;
        partisanSettingsDetailRow = -1;

        utyanRow = rowCount++;
        hintRow = rowCount++;
        changePasscodeRow = rowCount++;

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (
                    BiometricManager.from(ApplicationLoader.applicationContext).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS &&
                    AndroidUtilities.isKeyguardSecure()
                ) {
                    fingerprintRow = rowCount++;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        autoLockRow = rowCount++;
        autoLockDetailRow = rowCount++;

        if (!FakePasscodeUtils.isFakePasscodeActivated()) {
            fakePasscodesHeaderRow = rowCount++;
            if (!SharedConfig.fakePasscodes.isEmpty()) {
                firstFakePasscodeRow = rowCount;
                lastFakePasscodeRow = firstFakePasscodeRow + SharedConfig.fakePasscodes.size() - 1;
                rowCount = lastFakePasscodeRow + 1;
            }
            addFakePasscodeRow = rowCount++;
            restoreFakePasscodeDelimiterRow = rowCount++;
            restoreFakePasscodeRow = rowCount++;
            fakePasscodeDetailRow = rowCount++;
        }

        captureHeaderRow = rowCount++;
        captureRow = rowCount++;
        captureDetailRow = rowCount++;

        if (!FakePasscodeUtils.isFakePasscodeActivated()) {
            bruteForceProtectionRow = rowCount++;
            bruteForceProtectionDetailRow = rowCount++;

            clearCacheOnLockRow = rowCount++;
            clearCacheOnLockDetailRow = rowCount++;

            badPasscodeAttemptsRow = rowCount++;
            badPasscodePhotoFrontRow = rowCount++;
            badPasscodePhotoBackRow = rowCount++;
            if (SharedConfig.takePhotoWithBadPasscodeBack || SharedConfig.takePhotoWithBadPasscodeFront) {
                badPasscodeMuteAudioRow = rowCount++;
            }
            badPasscodeAttemptsDetailRow = rowCount++;

            partisanSettingsRow = rowCount++;
            partisanSettingsDetailRow = rowCount++;
        }

        disablePasscodeRow = rowCount++;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != TYPE_MANAGE_CODE_SETTINGS) {
            showKeyboard();
        }
    }

    private void showKeyboard() {
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
            if (!isCustomKeyboardVisible()) {
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[0]);
            }
        } else if (isPassword()) {
            passwordEditText.requestFocus();
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateFields() {
        String text;
        if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            text = LocaleController.getString(R.string.EnterYourPasscodeInfo);
        } else if (passcodeSetStep == 0) {
            text = LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreatePasscodeInfoPIN : R.string.CreatePasscodeInfoPassword);
        } else text = descriptionTextSwitcher.getCurrentView().getText().toString();

        boolean animate = !(descriptionTextSwitcher.getCurrentView().getText().equals(text) || TextUtils.isEmpty(descriptionTextSwitcher.getCurrentView().getText()));
        if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscodeInfo), animate);
        } else if (passcodeSetStep == 0) {
            descriptionTextSwitcher.setText(LocaleController.getString(currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreatePasscodeInfoPIN : R.string.CreatePasscodeInfoPassword), animate);
        }
        if (isPinCode()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, true, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, false, 1f, animate);
        } else if (isPassword()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, false, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, true, 1f, animate);
        }
        boolean show = isPassword();
        if (show) {
            onShowKeyboardCallback = () -> {
                setFloatingButtonVisible(show, animate);
                AndroidUtilities.cancelRunOnUIThread(onShowKeyboardCallback);
            };
            AndroidUtilities.runOnUIThread(onShowKeyboardCallback, 3000); // Timeout for floating keyboard
        } else {
            setFloatingButtonVisible(show, animate);
        }
        setCustomKeyboardVisible(isCustomKeyboardVisible(), animate);
        showKeyboard();
    }

    /**
     * @return If custom keyboard should be visible
     */
    private boolean isCustomKeyboardVisible() {
        return isPinCode() && type != TYPE_MANAGE_CODE_SETTINGS && !AndroidUtilities.isTablet() &&
                AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y && !AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private void processNext() {
        if (currentPasswordType == SharedConfig.PASSCODE_TYPE_PASSWORD && passwordEditText.getText().length() == 0 || currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN && codeFieldContainer.getCode().length() != 4) {
            onPasscodeError();
            return;
        }

        if (otherItem != null) {
            otherItem.setVisibility(View.GONE);
        }

        if (checkPasscodeInUse()) {
            return;
        }

        titleTextView.setText(LocaleController.getString(R.string.ConfirmCreatePasscode));
        descriptionTextSwitcher.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PasscodeReinstallNotice)));
        firstPassword = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        passwordEditText.setText("");
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for (CodeNumberField f : codeFieldContainer.codeField) f.setText("");
        showKeyboard();
        passcodeSetStep = 1;
    }

    private boolean checkPasscodeInUse() {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            return false;
        }
        String passcode = SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD
                ? passwordEditText.getText().toString()
                : codeFieldContainer.getCode();
        SharedConfig.PasscodeCheckResult passcodeCheckResult = SharedConfig.checkPasscode(passcode);
        if (passcodeCheckResult.fakePasscode != null) {
            showPasscodeError(ErrorType.PASSCODE_IN_USE);
            return true;
        }
        return false;
    }

    private boolean isPinCode() {
        return type == TYPE_SETUP_CODE && currentPasswordType == SharedConfig.PASSCODE_TYPE_PIN ||
                type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN;
    }

    private boolean isPassword() {
        return type == TYPE_SETUP_CODE && currentPasswordType == SharedConfig.PASSCODE_TYPE_PASSWORD ||
                type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD;
    }

    private void processDone() {
        if (isPassword() && passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        String password = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        if (type == TYPE_SETUP_CODE) {
            if (!firstPassword.equals(password) || isNewPasscodeIdenticalOtherPasscode(password)) {
                showPasscodeError(ErrorType.PASSCODES_DO_NOT_MATCH);
                return;
            }

            boolean isFirst = !SharedConfig.passcodeEnabled();
            try {
                if (FakePasscodeUtils.isFakePasscodeActivated()) {
                    FakePasscodeUtils.getActivatedFakePasscode().generatePasscodeHash(firstPassword);
                    FakePasscodeUtils.getActivatedFakePasscode().passwordDisabled = false;
                } else {
                    SharedConfig.passcodeSalt = new byte[16];
                    Utilities.random.nextBytes(SharedConfig.passcodeSalt);
                    byte[] passcodeBytes = firstPassword.getBytes("UTF-8");
                    byte[] bytes = new byte[32 + passcodeBytes.length];
                    System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
                    System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                    System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                    SharedConfig.setPasscode(Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length)));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (FakePasscodeUtils.getActivatedFakePasscode() != null) {
                SharedConfig.allowScreenCapture = true;
            }

            boolean passcodeTypeChanged = SharedConfig.passcodeType != currentPasswordType;
            if (passcodeTypeChanged) {
                MaskedMigratorHelper.removeMigrationIssueAndShowDialogIfNeeded(this, MaskedMigrationIssue.INVALID_PASSCODE_TYPE);
            }

            SharedConfig.passcodeType = currentPasswordType;
            SharedConfig.saveConfig();

            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.clearFocus();
                AndroidUtilities.hideKeyboard(f);
            }
            keyboardView.setEditText(null);

            animateSuccessAnimation(() -> {
                getMediaDataController().buildShortcuts();
                if (isFirst) {
                    presentFragment(new PasscodeActivity(TYPE_MANAGE_CODE_SETTINGS), true);
                } else {
                    finishFragment();
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            });
        } else if (type == TYPE_ENTER_CODE_TO_MANAGE_SETTINGS) {
            if (SharedConfig.passcodeRetryInMs > 0) {
                int value = Math.max(1, (int) Math.ceil(SharedConfig.passcodeRetryInMs / 1000.0));
                Toast.makeText(getParentActivity(), LocaleController.formatString("TooManyTries", R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)), Toast.LENGTH_SHORT).show();

                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
                passwordEditText.setText("");
                if (isPinCode()) {
                    codeFieldContainer.codeField[0].requestFocus();
                }
                onPasscodeError();
                return;
            }
            SharedConfig.PasscodeCheckResult result = SharedConfig.checkPasscode(password);
            synchronized (FakePasscode.class) {
                if (!result.allowLogin() || result.fakePasscode != null) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.PasscodeSettingsType, result.fakePasscode != null);
                    SharedConfig.addBadPasscodeAttempt(badAttempt);
                    badAttempt.takePhotos(getParentActivity());
                }
                if (!result.allowLogin() || result.isRealPasscodeSuccess && FakePasscodeUtils.isFakePasscodeActivated()
                    || result.fakePasscode != null && FakePasscodeUtils.getActivatedFakePasscode() != result.fakePasscode) {
                    SharedConfig.increaseBadPasscodeTries();
                    passwordEditText.setText("");
                    for (CodeNumberField f : codeFieldContainer.codeField) {
                        f.setText("");
                    }
                    if (isPinCode()) {
                        codeFieldContainer.codeField[0].requestFocus();
                    }
                    onPasscodeError();
                    return;
                }
                SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(result.fakePasscode));
                SharedConfig.badPasscodeTries = 0;
                SharedConfig.saveConfig();
            }
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.clearFocus();
                AndroidUtilities.hideKeyboard(f);
            }
            keyboardView.setEditText(null);

            animateSuccessAnimation(() -> {
                PasscodeActivity passcodeActivity = new PasscodeActivity(TYPE_MANAGE_CODE_SETTINGS);
                presentFragment(passcodeActivity, true);
            });
        }
    }

    private boolean isNewPasscodeIdenticalOtherPasscode(String password) {
        if (FakePasscodeUtils.isFakePasscodeActivated()) {
            SharedConfig.PasscodeCheckResult result = SharedConfig.checkPasscode(password);
            return result.isRealPasscodeSuccess
                    || result.fakePasscode != null && result.fakePasscode != FakePasscodeUtils.getActivatedFakePasscode();
        }
        return false;
    }

    private void showPasscodeError(ErrorType errorType) {
        if (errorType == ErrorType.PASSCODES_DO_NOT_MATCH) {
            passcodesErrorTextView.setText(LocaleController.getString(R.string.PasscodesDoNotMatchTryAgain));
        } else if (errorType == ErrorType.PASSCODE_IN_USE) {
            passcodesErrorTextView.setText(LocaleController.getString(R.string.PasscodeInUse));
        }

        AndroidUtilities.updateViewVisibilityAnimated(passcodesErrorTextView, true);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setText("");
        }
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
        }
        passwordEditText.setText("");
        onPasscodeError();

        codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
        codeFieldContainer.post(()->{
            codeFieldContainer.postDelayed(hidePasscodesDoNotMatch, 3000);
            postedHidePasscodesDoNotMatch = true;
        });
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
        AndroidUtilities.shakeViewSpring(isPinCode() ? codeFieldContainer : outlinePasswordView, isPinCode() ? 10 : 4, () -> AndroidUtilities.runOnUIThread(()->{
            if (isPinCode()) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateErrorProgress(0f);
                }
            } else {
                outlinePasswordView.animateError(0f);
            }
        }, isPinCode() ? 150 : 1000));
    }

    private void showPhotoWarning(Runnable callback) {
        if (SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack) {
            callback.run();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString(R.string.TakePhotoWarning));
            builder.setTitle(LocaleController.getString(R.string.Warning));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, v) -> callback.run());
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
        }
    }

    private void showPasswordToPinSwitchWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.Warning));
        builder.setMessage(LocaleController.getString(R.string.PasswordToPinSwitchWarning));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_CHECK = 0,
                VIEW_TYPE_SETTING = 1,
                VIEW_TYPE_INFO = 2,
                VIEW_TYPE_HEADER = 3,
                VIEW_TYPE_UTYAN = 4,
                VIEW_TYPE_SHADOW = 5;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == fingerprintRow || position == autoLockRow
                    || position == badPasscodeAttemptsRow || position == badPasscodePhotoFrontRow
                    || position == badPasscodePhotoBackRow || position == badPasscodeMuteAudioRow
                    || position == bruteForceProtectionRow || position == clearCacheOnLockRow
                    || position == captureRow || SharedConfig.passcodeEnabled() && position == changePasscodeRow
                    || (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == addFakePasscodeRow || position == restoreFakePasscodeRow
                    || position == partisanSettingsRow || position == disablePasscodeRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_UTYAN:
                    view = new RLottieImageHolderView(mContext);
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_SHADOW:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;

            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CHECK: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == fingerprintRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.UnlockFingerprint), SharedConfig.useFingerprintLock, false);
                    } else if (position == captureRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ScreenCaptureShowContent), SharedConfig.allowScreenCapture, false);
                    } else if (position == bruteForceProtectionRow) {
                        textCell.setTextAndCheck(LocaleController.getString("BruteForceProtection", R.string.BruteForceProtection), SharedConfig.bruteForceProtectionEnabled, false);
                    } else if (position == clearCacheOnLockRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearCacheOnLock", R.string.ClearCacheOnLock), SharedConfig.clearCacheOnLock, false);
                    } else if (position == badPasscodePhotoFrontRow) {
                        frontPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString("TakePhotoWithFrontCamera", R.string.TakePhotoWithFrontCamera), SharedConfig.takePhotoWithBadPasscodeFront, true);
                    } else if (position == badPasscodePhotoBackRow) {
                        backPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString("TakePhotoWithBackCamera", R.string.TakePhotoWithBackCamera), SharedConfig.takePhotoWithBadPasscodeBack, SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack);
                    } else if (position == badPasscodeMuteAudioRow) {
                        textCell.setTextAndCheck(LocaleController.getString("MuteAudioWhenTakingPhoto", R.string.MuteAudioWhenTakingPhoto), SharedConfig.takePhotoMuteAudio, false);

                    }
                    break;
                }
                case VIEW_TYPE_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changePasscodeRow) {
                        textCell.setText(LocaleController.getString(R.string.ChangePasscode), true);
                        if (!SharedConfig.passcodeEnabled()) {
                            textCell.setTag(Theme.key_windowBackgroundWhiteGrayText7);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                        } else {
                            textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        }
                    } else if (position == autoLockRow) {
                        String val;
                        if (SharedConfig.getAutoLockIn() == 0) {
                            val = LocaleController.formatString("AutoLockDisabled", R.string.AutoLockDisabled);
                        } else if (SharedConfig.getAutoLockIn() < 60) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Seconds", SharedConfig.getAutoLockIn()));
                        } else if (SharedConfig.getAutoLockIn() < 60 * 60) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", SharedConfig.getAutoLockIn() / 60));
                        } else if (SharedConfig.getAutoLockIn() < 60 * 60 * 24) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", (int) Math.ceil(SharedConfig.getAutoLockIn() / 60.0f / 60)));
                        } else {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Days", (int) Math.ceil(SharedConfig.getAutoLockIn() / 60.0f / 60 / 24)));
                        }
                        textCell.setTextAndValue(LocaleController.getString(R.string.AutoLock), val, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == disablePasscodeRow) {
                        textCell.setText(LocaleController.getString(R.string.DisablePasscode), false);
                        textCell.setTag(Theme.key_text_RedBold);
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    } else if (position == badPasscodeAttemptsRow) {
                        textCell.setTextAndValue(LocaleController.getString("BadPasscodeAttempts", R.string.BadPasscodeAttempts), String.valueOf(SharedConfig.getBadPasscodeAttemptList().size()),true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                        textCell.setText(SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow).name, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == addFakePasscodeRow) {
                        textCell.setText(LocaleController.getString("AddFakePasscode", R.string.AddFakePasscode), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == restoreFakePasscodeRow) {
                        textCell.setText(LocaleController.getString("FakePasscodeRestore", R.string.FakePasscodeRestore), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == partisanSettingsRow) {
                        textCell.setText(LocaleController.getString("PartisanSettings", R.string.PartisanSettings), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setHeight(46);
                    if (position == captureHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.ScreenCaptureHeader));
                    } else if (position == fakePasscodesHeaderRow) {
                        cell.setText(LocaleController.getString("FakePasscodes", R.string.FakePasscodes));
                    }
                    break;
                }
                case VIEW_TYPE_UTYAN: {
                    RLottieImageHolderView holderView = (RLottieImageHolderView) holder.itemView;
                    holderView.imageView.setAnimation(R.raw.utyan_passcode, 100, 100);
                    holderView.imageView.playAnimation();
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == hintRow) {
                        cell.setText(LocaleController.getString(R.string.PasscodeScreenHint));
                        cell.setBackground(null);
                        cell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
                    } else if (position == autoLockDetailRow) {
                        cell.setText(LocaleController.getString(R.string.AutoLockInfo));
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == captureDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ScreenCaptureInfo));
                        cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == bruteForceProtectionDetailRow) {
                        cell.setText(LocaleController.getString("BruteForceProtectionInfo", R.string.BruteForceProtectionInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == clearCacheOnLockDetailRow) {
                        cell.setText(LocaleController.getString("ClearCacheOnLockInfo", R.string.ClearCacheOnLockInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == badPasscodeAttemptsDetailRow) {
                        cell.setText(LocaleController.getString("BadPasscodeAttemptsInfo", R.string.BadPasscodeAttemptsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == fakePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("FakePasscodeActionsInfo", R.string.FakePasscodeActionsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == partisanSettingsDetailRow) {
                        cell.setText(LocaleController.getString("PartisanSettingsInfo", R.string.PartisanSettingsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    }
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == fingerprintRow || position == captureRow
                    || position == bruteForceProtectionRow || position == clearCacheOnLockRow
                    || position == badPasscodePhotoFrontRow || position == badPasscodePhotoBackRow
                    || position == badPasscodeMuteAudioRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == changePasscodeRow || position == autoLockRow || position == disablePasscodeRow
                    || position == addFakePasscodeRow || position == restoreFakePasscodeRow
                    || position == badPasscodeAttemptsRow
                    || (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == partisanSettingsRow) {
                return VIEW_TYPE_SETTING;
            } else if (position == autoLockDetailRow || position == captureDetailRow || position == hintRow
                    || position == bruteForceProtectionDetailRow || position == clearCacheOnLockDetailRow
                    || position == badPasscodeAttemptsDetailRow || position == fakePasscodeDetailRow
                    || position == partisanSettingsDetailRow) {
                return VIEW_TYPE_INFO;
            } else if (position == fakePasscodesHeaderRow || position == captureHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == utyanRow) {
                return VIEW_TYPE_UTYAN;
            } else if (position == restoreFakePasscodeDelimiterRow) {
                return VIEW_TYPE_SHADOW;
            }
            return VIEW_TYPE_CHECK;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }

    private final static class RLottieImageHolderView extends FrameLayout {
        private RLottieImageView imageView;

        private RLottieImageHolderView(@NonNull Context context) {
            super(context);
            imageView = new RLottieImageView(context);
            imageView.setOnClickListener(v -> {
                if (!imageView.getAnimatedDrawable().isRunning()) {
                    imageView.getAnimatedDrawable().setCurrentFrame(0, false);
                    imageView.playAnimation();
                }
            });
            int size = AndroidUtilities.dp(120);
            LayoutParams params = new LayoutParams(size, size);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            addView(imageView, params);

            setPadding(0, AndroidUtilities.dp(32), 0, 0);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
