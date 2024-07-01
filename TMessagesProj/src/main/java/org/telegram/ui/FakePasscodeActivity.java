/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import androidx.annotation.IntDef;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.AccountActions;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeSerializer;
import org.telegram.messenger.fakepasscode.SelectionMode;
import org.telegram.messenger.fakepasscode.UpdateIdHashRunnable;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AccountActionsCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class FakePasscodeActivity extends BaseFragment {
    public final static int TYPE_FAKE_PASSCODE_SETTINGS = 0,
            TYPE_SETUP_FAKE_PASSCODE = 1,
            TYPE_ENTER_BACKUP_CODE = 2,
            TYPE_ENTER_RESTORE_CODE = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TYPE_FAKE_PASSCODE_SETTINGS,
            TYPE_SETUP_FAKE_PASSCODE,
            TYPE_ENTER_BACKUP_CODE,
            TYPE_ENTER_RESTORE_CODE
    })
    public @interface FakePasscodeActivityType {}

    private enum ErrorType {
        PASSCODES_DO_NOT_MATCH,
        PASSCODE_IN_USE
    }

    private RLottieImageView lockImageView;

    private TextSettingsCell changeNameCell;

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

    @FakePasscodeActivityType
    private int type;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int rowCount;

    private int changeNameRow;
    private int changeFakePasscodeRow;
    private int otherActivationMethodsRow;
    private int changeFakePasscodeDetailRow;
    private int accountHeaderRow;
    private int firstAccountRow;
    private int lastAccountRow;
    private int accountDetailRow;

    private int actionsHeaderRow;
    private int smsRow;
    private int clearTelegramCacheRow;
    private int clearTelegramDownloadsRow;
    private int clearProxiesRow;

    private int clearAfterActivationRow;
    private int clearAfterActivationDetailRow;

    private int deleteOtherPasscodesAfterActivationRow;
    private int deleteOtherPasscodesAfterActivationDetailRow;

    private int passwordlessModeRow;
    private int passwordlessModeDetailRow;
    private int replaceOriginalPasscodeRow;
    private int replaceOriginalPasscodeDetailRow;
    private int allowFakePasscodeLoginRow;
    private int allowFakePasscodeLoginDetailRow;

    private int backupPasscodeRow;
    private int backupPasscodeDetailRow;

    private int deletePasscodeRow;
    private int deletePasscodeDetailRow;

    List<AccountActionsCellInfo> accounts = new ArrayList<>();

    private boolean creating;
    private FakePasscode fakePasscode;
    private byte[] encryptedPasscode;

    TextCheckCell allowFakePasscodeLoginCell;

    private boolean postedHidePasscodesDoNotMatch;
    private Runnable hidePasscodesDoNotMatch = () -> {
        postedHidePasscodesDoNotMatch = false;
        AndroidUtilities.updateViewVisibilityAnimated(passcodesErrorTextView, false);
    };

    private Runnable onShowKeyboardCallback;

    private final static int done_button = 1;

    public FakePasscodeActivity(@FakePasscodeActivityType int type, FakePasscode fakePasscode, boolean creating) {
        super();
        this.type = type;
        this.fakePasscode = fakePasscode;
        this.creating = creating;
    }

    public FakePasscodeActivity(byte[] encodedPasscode) {
        super();
        this.type = TYPE_ENTER_RESTORE_CODE;
        this.encryptedPasscode = encodedPasscode;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
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
                } else if (id == done_button) {
                    if (type == TYPE_SETUP_FAKE_PASSCODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else if (passcodeSetStep == 1) {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_BACKUP_CODE || type == TYPE_ENTER_RESTORE_CODE) {
                        processDone();
                    }
                }
            }
        });

        View fragmentContentView;
        FrameLayout frameLayout = new FrameLayout(context);
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
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
            case TYPE_FAKE_PASSCODE_SETTINGS: {
                actionBar.setTitle(LocaleController.getString("FakePasscode", R.string.FakePasscode));
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
                listView.setOnItemClickListener((view, position, x, y) -> {
                    if (!view.isEnabled()) {
                        if (position == allowFakePasscodeLoginRow) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            String blockingSetting = "";
                            if (fakePasscode.activateByFingerprint) {
                                blockingSetting = LocaleController.getString(R.string.ActivateWithFingerprint);
                            } else if (fakePasscode.replaceOriginalPasscode) {
                                blockingSetting = LocaleController.getString(R.string.ReplaceOriginalPasscode);
                            } else if (fakePasscode.passwordlessMode) {
                                blockingSetting = LocaleController.getString(R.string.PasswordlessMode);
                            }
                            builder.setMessage(LocaleController.formatString(R.string.CannotDisableLoginIfOptionEnabledDescription, blockingSetting));
                            builder.setTitle(LocaleController.getString(R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                        } else if (position == passwordlessModeRow) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.formatString(R.string.CannotEnableSettingDescription, LocaleController.getString(R.string.ReplaceOriginalPasscode)));
                            builder.setTitle(LocaleController.getString(R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                        }
                        return;
                    }
                    if (position == changeNameRow) {
                        AlertDialog.Builder alert = new AlertDialog.Builder(getParentActivity());
                        final EditTextCaption editText = new EditTextCaption(getParentActivity(), null);
                        editText.setText(fakePasscode.name);
                        editText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
                        editText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
                        editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
                        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
                        alert.setTitle(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName));
                        alert.setView(editText);
                        alert.setPositiveButton(LocaleController.getString("Done", R.string.Done), (dialog, whichButton) -> {
                            fakePasscode.name = editText.getText().toString();
                            SharedConfig.saveConfig();
                            changeNameCell.setTextAndValue(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName),
                                    fakePasscode.name, true);
                        });
                        showDialog(alert.create());
                    } else if (position == changeFakePasscodeRow) {
                        presentFragment(new FakePasscodeActivity(TYPE_SETUP_FAKE_PASSCODE, fakePasscode, false));
                    } else if (position == otherActivationMethodsRow) {
                        FakePasscodeActivationMethodsActivity activity = new FakePasscodeActivationMethodsActivity(fakePasscode);
                        presentFragment(activity);
                    } else if (position == smsRow) {
                        FakePasscodeSmsActivity activity = new FakePasscodeSmsActivity(fakePasscode.smsAction);
                        presentFragment(activity);
                    } else if (position == clearTelegramCacheRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearCacheAction.enabled = !fakePasscode.clearCacheAction.enabled;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearCacheAction.enabled);
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    } else if (position == clearTelegramDownloadsRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearDownloadsAction.enabled = !fakePasscode.clearDownloadsAction.enabled;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearDownloadsAction.enabled);
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    } else if (position == clearProxiesRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearProxiesAction.enabled = !fakePasscode.clearProxiesAction.enabled;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearProxiesAction.enabled);
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    } else if (position == clearAfterActivationRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearAfterActivation = !fakePasscode.clearAfterActivation;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearAfterActivation);
                    } else if (position == deleteOtherPasscodesAfterActivationRow) {
                        if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                            NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                            fakePasscode.deletePasscodesAfterActivation.setSelected(Collections.emptyList());
                            if (fakePasscode.deletePasscodesAfterActivation.isEnabled()) {
                                fakePasscode.deletePasscodesAfterActivation.setMode(SelectionMode.SELECTED);
                            } else {
                                fakePasscode.deletePasscodesAfterActivation.setMode(SelectionMode.EXCEPT_SELECTED);
                            }
                            SharedConfig.saveConfig();
                            boolean enabled = fakePasscode.deletePasscodesAfterActivation.isEnabled();
                            String value;
                            if (!enabled) {
                                value = LocaleController.getString(R.string.PopupDisabled);
                            } else if (fakePasscode.deletePasscodesAfterActivation.getMode() == SelectionMode.SELECTED) {
                                value = LocaleController.getString("Selected", R.string.Selected);
                            } else {
                                value = LocaleController.getString("ExceptSelected", R.string.ExceptSelected);
                            }
                            checkCell.setTextAndValueAndCheck(LocaleController.getString("DeleteOtherPasscodesAfterActivation", R.string.DeleteOtherPasscodesAfterActivation), value, enabled, false);
                        } else {
                            presentFragment(new FakePasscodeRemovePasscodesActivity(fakePasscode));
                        }
                    } else if (position == passwordlessModeRow) {
                        Runnable toggleSetting = () -> {
                            TextCheckCell cell = (TextCheckCell) view;
                            fakePasscode.passwordlessMode = !fakePasscode.passwordlessMode;
                            SharedConfig.saveConfig();
                            cell.setChecked(fakePasscode.passwordlessMode);
                            updateRows();
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        };
                        if (!fakePasscode.passwordlessMode && fakePasscode.hasPasswordlessIncompatibleSettings()) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString(R.string.RemovePasswordlessIncompatibleSettingsDescription));
                            builder.setTitle(LocaleController.getString(R.string.RemoveIncompatibleSettingsTitle));
                            builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> {
                                fakePasscode.removePasswordlessIncompatibleSettings();
                                toggleSetting.run();
                            });
                            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                        } else {
                            toggleSetting.run();
                        }
                    } else if (position == replaceOriginalPasscodeRow) {
                        Runnable toggleSetting = () -> {
                            TextCheckCell cell = (TextCheckCell) view;
                            fakePasscode.replaceOriginalPasscode = !fakePasscode.replaceOriginalPasscode;
                            SharedConfig.saveConfig();
                            cell.setChecked(fakePasscode.replaceOriginalPasscode);
                            updateRows();
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        };
                        if (!fakePasscode.replaceOriginalPasscode && fakePasscode.hasReplaceOriginalPasscodeIncompatibleSettings()) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString(R.string.RemoveReplaceOriginalPasscodeIncompatibleSettingsDescription));
                            builder.setTitle(LocaleController.getString(R.string.RemoveIncompatibleSettingsTitle));
                            builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> {
                                fakePasscode.removeReplaceOriginalPasscodeIncompatibleSettings();
                                toggleSetting.run();
                            });
                            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                        } else {
                            toggleSetting.run();
                        }
                    } else if (firstAccountRow != -1 && firstAccountRow <= position && position <= lastAccountRow) {
                        AccountActionsCellInfo info = accounts.get(position - firstAccountRow);
                        if (info.accountNum != null) {
                            AccountActions actions = fakePasscode.getOrCreateAccountActions(info.accountNum);
                            presentFragment(new FakePasscodeAccountActionsActivity(actions, fakePasscode), false);
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("DeleteOldAccountActionsInfo", R.string.DeleteOldAccountActionsInfo));
                            builder.setTitle(LocaleController.getString("DeleteOldAccountActions", R.string.DeleteOldAccountActions));
                            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                                fakePasscode.accountActions.remove(info.actions);
                                SharedConfig.saveConfig();
                                updateRows();
                                listAdapter.notifyDataSetChanged();
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_color_red));
                            }
                        }
                    } else if (position == allowFakePasscodeLoginRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.allowLogin = !fakePasscode.allowLogin;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.allowLogin);
                    } else if (position == backupPasscodeRow) {
                        FakePasscodeActivity activity = new FakePasscodeActivity(TYPE_ENTER_BACKUP_CODE, fakePasscode, false);
                        presentFragment(activity);
                    } else if (position == deletePasscodeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteFakePasscode", R.string.AreYouSureDeleteFakePasscode));
                        builder.setTitle(LocaleController.getString("DeleteFakePasscode", R.string.DeleteFakePasscode));
                        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                            fakePasscode.onDelete();
                            SharedConfig.fakePasscodes = SharedConfig.fakePasscodes.stream()
                                    .filter(a -> a != fakePasscode).collect(Collectors.toCollection(ArrayList::new));
                            if (SharedConfig.fakePasscodes.isEmpty()) {
                                SharedConfig.fakePasscodeIndex = 1;
                            }
                            SharedConfig.saveConfig();
                            finishFragment();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        AlertDialog alertDialog = builder.create();
                        showDialog(alertDialog);
                        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_color_red));
                        }
                    }
                });
                break;
            }
            case TYPE_ENTER_BACKUP_CODE:
            case TYPE_ENTER_RESTORE_CODE:
            case TYPE_SETUP_FAKE_PASSCODE: {
                if (actionBar != null) {
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
                }

                FrameLayout codeContainer = new FrameLayout(context);

                LinearLayout innerLinearLayout = new LinearLayout(context);
                innerLinearLayout.setOrientation(LinearLayout.VERTICAL);
                innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                frameLayout.addView(innerLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                lockImageView = new RLottieImageView(context);
                lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
                lockImageView.setAutoRepeat(false);
                lockImageView.playAnimation();
                lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
                innerLinearLayout.addView(lockImageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

                titleTextView = new TextView(context);
                titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                if (type == TYPE_SETUP_FAKE_PASSCODE) {
                    if (SharedConfig.passcodeEnabled()) {
                        titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
                    } else {
                        titleTextView.setText(LocaleController.getString("EnterNewFirstPasscode", R.string.EnterNewFirstPasscode));
                    }
                } else {
                    titleTextView.setText(LocaleController.getString("EnterCurrentFakePasscode", R.string.EnterCurrentFakePasscode));
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
                if (type == TYPE_SETUP_FAKE_PASSCODE) {
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
                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, type == TYPE_SETUP_FAKE_PASSCODE && passcodeSetStep == 0, 0.1f, false);

                AtomicBoolean isPasswordShown = new AtomicBoolean(false);
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (type == TYPE_SETUP_FAKE_PASSCODE && passcodeSetStep == 0) {
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

                if (type == TYPE_SETUP_FAKE_PASSCODE) {
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
                    if (type == TYPE_SETUP_FAKE_PASSCODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_BACKUP_CODE || type == TYPE_ENTER_RESTORE_CODE) {
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
        return type != TYPE_FAKE_PASSCODE_SETTINGS;
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
            if (keyboardView != null) {
                keyboardView.setVisibility(visible ? View.VISIBLE : View.GONE);
                keyboardView.setAlpha(visible ? 1 : 0);
                keyboardView.setTranslationY(visible ? 0 : AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                if (fragmentView != null) {
                    fragmentView.requestLayout();
                }
            }
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? CubicBezierInterpolator.DEFAULT : Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                if (keyboardView != null) {
                    float val = (float) animation.getAnimatedValue();
                    keyboardView.setAlpha(val);
                    keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) * 0.75f);
                    fragmentView.requestLayout();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible && keyboardView != null) {
                        keyboardView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible && keyboardView != null) {
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
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != TYPE_FAKE_PASSCODE_SETTINGS && !isCustomKeyboardVisible()) {
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

    private void updateRows() {
        rowCount = 0;

        changeNameRow = rowCount++;
        changeFakePasscodeRow = rowCount++;
        otherActivationMethodsRow = rowCount++;
        changeFakePasscodeDetailRow = rowCount++;

        accountHeaderRow = rowCount++;
        firstAccountRow = rowCount;
        lastAccountRow = firstAccountRow - 1;
        accounts.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(new AccountActionsCellInfo(a));
                lastAccountRow = rowCount++;
            }
        }
        Collections.sort(accounts, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1.accountNum).loginTime;
            long l2 = UserConfig.getInstance(o2.accountNum).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });
        if (fakePasscode != null) {
            for (AccountActions actions : fakePasscode.accountActions) {
                if (actions.getAccountNum() == null) {
                    accounts.add(new AccountActionsCellInfo(actions));
                    lastAccountRow = rowCount++;
                }
            }
        }

        accountDetailRow = rowCount++;

        actionsHeaderRow = rowCount++;
        if (fakePasscode != null && fakePasscode.smsAction != null
                && fakePasscode.smsAction.messages != null
                && !fakePasscode.smsAction.messages.isEmpty()) {
            smsRow = rowCount++;
        } else {
            smsRow = -1;
        }
        clearTelegramCacheRow = rowCount++;
        clearTelegramDownloadsRow = rowCount++;
        clearProxiesRow = rowCount++;

        clearAfterActivationRow =  rowCount++;
        clearAfterActivationDetailRow =  rowCount++;

        deleteOtherPasscodesAfterActivationRow =  rowCount++;
        deleteOtherPasscodesAfterActivationDetailRow =  rowCount++;

        passwordlessModeRow = rowCount++;
        passwordlessModeDetailRow = rowCount++;

        replaceOriginalPasscodeRow = rowCount++;
        replaceOriginalPasscodeDetailRow = rowCount++;

        allowFakePasscodeLoginRow = rowCount++;
        allowFakePasscodeLoginDetailRow = rowCount++;

        backupPasscodeRow = rowCount++;
        backupPasscodeDetailRow = rowCount++;

        deletePasscodeRow = rowCount++;
        deletePasscodeDetailRow = rowCount++;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != TYPE_FAKE_PASSCODE_SETTINGS) {
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
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
            text = LocaleController.getString(R.string.EnterYourPasscodeInfo);
        } else if (passcodeSetStep == 0) {
            text = LocaleController.getString(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreateFakePasscodeInfoPIN : R.string.CreateFakePasscodeInfoPassword);
        } else text = descriptionTextSwitcher.getCurrentView().getText().toString();

        boolean animate = !(descriptionTextSwitcher.getCurrentView().getText().equals(text) || TextUtils.isEmpty(descriptionTextSwitcher.getCurrentView().getText()));
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
            descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscodeInfo), animate);
        } else if (passcodeSetStep == 0) {
            descriptionTextSwitcher.setText(LocaleController.getString(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreateFakePasscodeInfoPIN : R.string.CreateFakePasscodeInfoPassword), animate);
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
        return isPinCode() && type != TYPE_FAKE_PASSCODE_SETTINGS && !AndroidUtilities.isTablet() &&
                AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y && !AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private void processNext() {
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD && passwordEditText.getText().length() == 0 || SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN && codeFieldContainer.getCode().length() != 4) {
            onPasscodeError();
            return;
        }

        String code;
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            code = passwordEditText.getText().toString();
        } else {
            code = codeFieldContainer.getCode();
        }
        SharedConfig.PasscodeCheckResult passcodeCheckResult = SharedConfig.checkPasscode(code);
        if (passcodeCheckResult.isRealPasscodeSuccess || passcodeCheckResult.fakePasscode != null) {
            showPasscodeError(ErrorType.PASSCODE_IN_USE);
            return;
        }

        titleTextView.setText(LocaleController.getString("ConfirmCreatePasscode", R.string.ConfirmCreatePasscode));
        descriptionTextSwitcher.setText("");
        firstPassword = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        passwordEditText.setText("");
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for (CodeNumberField f : codeFieldContainer.codeField) f.setText("");
        showKeyboard();
        passcodeSetStep = 1;
    }

    private boolean isPinCode() {
        return SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN;
    }

    private boolean isPassword() {
        return SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD;
    }

    private void processDone() {
        if (isPassword() && passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        if (type == TYPE_SETUP_FAKE_PASSCODE) {
            processDoneSetup();
        } else if (type == TYPE_ENTER_BACKUP_CODE) {
            processDoneBackup();
        } else if (type == TYPE_ENTER_RESTORE_CODE) {
            processDoneRestore();
        }
    }

    private void processDoneSetup() {
        String password = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        if (!firstPassword.equals(password)) {
            showPasscodeError(ErrorType.PASSCODES_DO_NOT_MATCH);
            return;
        }

        fakePasscode.passcodeHash = FakePasscodeSerializer.calculateHash(firstPassword, SharedConfig.passcodeSalt);
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
            if (creating) {
                SharedConfig.fakePasscodes.add(fakePasscode);
                SharedConfig.fakePasscodeIndex++;
                SharedConfig.saveConfig();
                presentFragment(new FakePasscodeActivity(TYPE_FAKE_PASSCODE_SETTINGS, fakePasscode, false), true);
            } else {
                finishFragment();
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        });
    }

    private void processDoneBackup() {
        String passcodeString = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        if (Objects.equals(FakePasscodeSerializer.calculateHash(passcodeString, SharedConfig.passcodeSalt), fakePasscode.passcodeHash)) {
            presentFragment(new FakePasscodeBackupActivity(fakePasscode, passcodeString), true);
        } else {
            showPasscodeError(ErrorType.PASSCODES_DO_NOT_MATCH);
        }
    }

    private void processDoneRestore() {
        AccountActions.Companion.setUpdateIdHashEnabled(false);
        String passcodeString = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        FakePasscode passcode = FakePasscodeSerializer.deserializeEncrypted(encryptedPasscode, passcodeString);
        if (passcode == null) {
            showPasscodeError(ErrorType.PASSCODES_DO_NOT_MATCH);
        } else if (SharedConfig.fakePasscodes.stream().anyMatch(p -> p.passcodeHash.equals(passcode.passcodeHash))) {
            showPasscodeError(ErrorType.PASSCODE_IN_USE);
        } else {
            SharedConfig.fakePasscodes.add(passcode);
            passcode.accountActions.stream().forEach(a -> a.setAccountNum(null));
            passcode.accountActions.stream().forEach(a -> a.checkAccountNum());
            passcode.autoAddAccountHidings();
            SharedConfig.saveConfig();
            if (parentLayout.getFragmentStack().size() >= 2) {
                parentLayout.removeFragmentFromStack(parentLayout.getFragmentStack().size() - 2);
            }
            presentFragment(new FakePasscodeActivity(TYPE_FAKE_PASSCODE_SETTINGS, passcode, false), true);
        }
        AccountActions.Companion.setUpdateIdHashEnabled(true);
        if (passcode != null) {
            passcode.accountActions.stream().forEach(a ->
                    Utilities.globalQueue.postRunnable(new UpdateIdHashRunnable(a), 1000));
        }
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

    private class AccountActionsCellInfo {
        public Integer accountNum;
        public AccountActions actions;

        public AccountActionsCellInfo(Integer accountNum) {
            this.accountNum = accountNum;
        }

        public AccountActionsCellInfo(AccountActions actions) {
            this.actions = actions;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_CHECK = 0,
                VIEW_TYPE_SETTING = 1,
                VIEW_TYPE_INFO = 2,
                VIEW_TYPE_ACCOUNT_ACTIONS = 3,
                VIEW_TYPE_HEADER = 4,
                VIEW_TYPE_SHADOW = 5,
                VIEW_TYPE_EXTENDED_CHECK = 6;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == changeNameRow || position == changeFakePasscodeRow
                    || position == otherActivationMethodsRow
                    || (firstAccountRow <= position && position <= lastAccountRow)
                    || position == smsRow || position == clearTelegramCacheRow
                    || position == clearProxiesRow || position ==  clearAfterActivationRow
                    || position == deleteOtherPasscodesAfterActivationRow
                    || (position == passwordlessModeRow && !fakePasscode.replaceOriginalPasscode)
                    || position == replaceOriginalPasscodeRow
                    || (position == allowFakePasscodeLoginRow && !fakePasscode.replaceOriginalPasscode
                        && !fakePasscode.activateByFingerprint && !fakePasscode.passwordlessMode)
                    || position == backupPasscodeRow || position == clearTelegramDownloadsRow
                    || position == deletePasscodeRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_ACCOUNT_ACTIONS:
                {
                    AccountActionsCell cell = new AccountActionsCell(mContext);
                    view = cell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_EXTENDED_CHECK:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                    if (position == clearTelegramCacheRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearTelegramCacheOnFakeLogin", R.string.ClearTelegramCacheOnFakeLogin), fakePasscode.clearCacheAction.enabled, true);
                    } else if (position == clearTelegramDownloadsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearTelegramDownloadsOnFakeLogin", R.string.ClearTelegramDownloadsOnFakeLogin), fakePasscode.clearDownloadsAction.enabled, false);
                    } else if (position == clearProxiesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearProxiesOnFakeLogin", R.string.ClearProxiesOnFakeLogin), fakePasscode.clearProxiesAction.enabled, false);
                    } else if (position == clearAfterActivationRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearAfterActivation", R.string.ClearAfterActivation), fakePasscode.clearAfterActivation, false);
                    } else if (position == passwordlessModeRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.PasswordlessMode), fakePasscode.passwordlessMode, false);
                    } else if (position == replaceOriginalPasscodeRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ReplaceOriginalPasscode), fakePasscode.replaceOriginalPasscode, false);
                    } else if (position == allowFakePasscodeLoginRow) {
                        allowFakePasscodeLoginCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString("AllowFakePasscodeLogin", R.string.AllowFakePasscodeLogin), fakePasscode.allowLogin, false);
                    }
                    break;
                }
                case VIEW_TYPE_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changeNameRow) {
                        changeNameCell = textCell;
                        textCell.setTextAndValue(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName), fakePasscode.name, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == changeFakePasscodeRow) {
                        textCell.setText(LocaleController.getString("ChangeFakePasscode", R.string.ChangeFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == otherActivationMethodsRow) {
                        textCell.setText(LocaleController.getString(R.string.OtherActivationMethods), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == smsRow) {
                        textCell.setTextAndValue(LocaleController.getString("FakePasscodeSmsActionTitle", R.string.FakePasscodeSmsActionTitle), String.valueOf(fakePasscode.smsAction.messages.size()), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == backupPasscodeRow) {
                        textCell.setText(LocaleController.getString("BackupFakePasscode", R.string.BackupFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == deletePasscodeRow) {
                        textCell.setText(LocaleController.getString("DeleteFakePasscode", R.string.DeleteFakePasscode), false);
                        textCell.setTag(Theme.key_color_red);
                        textCell.setTextColor(Theme.getColor(Theme.key_color_red));
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == allowFakePasscodeLoginDetailRow) {
                        cell.setText(LocaleController.getString("AllowFakePasscodeLoginInfo", R.string.AllowFakePasscodeLoginInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == clearAfterActivationDetailRow) {
                        cell.setText(LocaleController.getString("ClearAfterActivationDetails", R.string.ClearAfterActivationDetails));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == deleteOtherPasscodesAfterActivationDetailRow) {
                        cell.setText(LocaleController.getString("DeleteOtherPasscodesAfterActivationDetails", R.string.DeleteOtherPasscodesAfterActivationDetails));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordlessModeDetailRow) {
                        cell.setText(LocaleController.getString(R.string.PasswordlessModeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == replaceOriginalPasscodeDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ReplaceOriginalPasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == accountDetailRow) {
                        cell.setText(LocaleController.getString("FakePasscodeAccountsInfo", R.string.FakePasscodeAccountsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == backupPasscodeDetailRow) {
                        cell.setText(LocaleController.getString("BackupFakePasscodeInfo", R.string.BackupFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == deletePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("DeleteFakePasscodeInfo", R.string.DeleteFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_ACCOUNT_ACTIONS: {
                    AccountActionsCell cell = (AccountActionsCell) holder.itemView;
                    AccountActionsCellInfo info = accounts.get(position - firstAccountRow);
                    cell.setAccount(info.accountNum, info.actions, position != lastAccountRow);
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == actionsHeaderRow) {
                        headerCell.setText(LocaleController.getString("FakePasscodeActionsHeader", R.string.FakePasscodeActionsHeader));
                    } else if (position == accountHeaderRow) {
                        headerCell.setText(LocaleController.getString("FakePasscodeAccountsHeader", R.string.FakePasscodeAccountsHeader));
                    }
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                }
                case VIEW_TYPE_EXTENDED_CHECK: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == deleteOtherPasscodesAfterActivationRow) {
                        fakePasscode.deletePasscodesAfterActivation.verifySelected();
                        boolean enabled = fakePasscode.deletePasscodesAfterActivation.isEnabled();
                        String value;
                        if (!enabled) {
                            value = LocaleController.getString(R.string.PopupDisabled);
                        } else if (fakePasscode.deletePasscodesAfterActivation.getMode() == SelectionMode.SELECTED) {
                            value = LocaleController.getString("Selected", R.string.Selected);
                        } else {
                            value = LocaleController.getString("ExceptSelected", R.string.ExceptSelected);
                        }
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("DeleteOtherPasscodesAfterActivation", R.string.DeleteOtherPasscodesAfterActivation), value, enabled, false);
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) {
                TextCheckCell textCell = (TextCheckCell) holder.itemView;
                if (holder.getAdapterPosition() == allowFakePasscodeLoginRow) {
                    boolean enabled = !fakePasscode.activateByFingerprint && !fakePasscode.replaceOriginalPasscode
                            && !fakePasscode.passwordlessMode;
                    textCell.setEnabled(enabled, null);
                } else if (holder.getAdapterPosition() == passwordlessModeRow) {
                    textCell.setEnabled(!fakePasscode.replaceOriginalPasscode, null);
                } else {
                    textCell.setEnabled(isEnabled(holder), null);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == clearTelegramCacheRow || position == clearProxiesRow
                    || position == clearAfterActivationRow || position == passwordlessModeRow
                    || position == replaceOriginalPasscodeRow || position == allowFakePasscodeLoginRow
                    || position == clearTelegramDownloadsRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == changeNameRow || position == changeFakePasscodeRow
                    || position == otherActivationMethodsRow || position == smsRow
                    || position == backupPasscodeRow || position == deletePasscodeRow) {
                return VIEW_TYPE_SETTING;
            } else if (position == allowFakePasscodeLoginDetailRow || position == clearAfterActivationDetailRow
                    || position == deleteOtherPasscodesAfterActivationDetailRow
                    || position == passwordlessModeDetailRow || position == replaceOriginalPasscodeDetailRow
                    || position == accountDetailRow || position == backupPasscodeDetailRow
                    || position == deletePasscodeDetailRow) {
                return VIEW_TYPE_INFO;
            } else if (firstAccountRow <= position && position <= lastAccountRow) {
                return VIEW_TYPE_ACCOUNT_ACTIONS;
            } else if (position == actionsHeaderRow || position == accountHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == changeFakePasscodeDetailRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == deleteOtherPasscodesAfterActivationRow) {
                return VIEW_TYPE_EXTENDED_CHECK;
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
}
