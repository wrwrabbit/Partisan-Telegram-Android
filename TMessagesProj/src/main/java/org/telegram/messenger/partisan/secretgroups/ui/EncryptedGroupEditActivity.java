package org.telegram.messenger.partisan.secretgroups.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupProtocol;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupState;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupUtils;
import org.telegram.messenger.partisan.secretgroups.InnerEncryptedChat;
import org.telegram.messenger.partisan.secretgroups.action.ChangeGroupInfoAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.AllowShowingActivityInterface;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.EncryptedGroupMemberCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoCropActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class EncryptedGroupEditActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate,
        AllowShowingActivityInterface,
        PhotoCropActivity.PhotoEditActivityDelegate {
    private int addMemberRow;
    private int selfRow;
    private int firstMemberRow;
    private int lastMemberRow;
    private int rowCount;

    private View doneButton;
    private AlertDialog progressDialog;

    private LinearLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private EditTextEmoji nameTextView;
    private LinearLayout settingsContainer;
    private TextCell setAvatarCell;
    private TextCell deleteAvatarCell;

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private final EncryptedGroup encryptedGroup;
    private Set<Long> membersCurrentlyRemoving = new HashSet<>();

    private boolean donePressed;

    private final static int done_button = 1;

    public EncryptedGroupEditActivity(EncryptedGroup encryptedGroup) {
        super();
        avatarDrawable = new AvatarDrawable();
        this.encryptedGroup = encryptedGroup;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        updateRows();

        getNotificationCenter().addObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.encryptedGroupMemberRemoved);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.encryptedGroupMemberRemoved);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        if (nameTextView != null) {
            nameTextView.onDestroy();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20)) {
                    ignoreLayout = true;
                    nameTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? nameTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (nameTextView != null && nameTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };

        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        sizeNotifierFrameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(getString("ChannelEdit", R.string.ChannelEdit));

        avatarContainer = new LinearLayout(context);
        avatarContainer.setOrientation(LinearLayout.VERTICAL);
        avatarContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(avatarContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayout(context);
        avatarContainer.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context) {
            @Override
            public void invalidate() {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate();
            }

            @Override
            public void invalidate(int l, int t, int r, int b) {
                if (avatarOverlay != null) {
                    avatarOverlay.invalidate();
                }
                super.invalidate(l, t, r, b);
            }
        };
        avatarImage.setRoundRadius(dp(32));

        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);

        avatarOverlay = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (avatarImage != null && avatarImage.getImageReceiver().hasNotThumb()) {
                    paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha()));
                    canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                }
            }
        };
        frameLayout.addView(avatarOverlay, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

        avatarProgressView = new RadialProgressView(context);
        avatarProgressView.setSize(dp(30));
        avatarProgressView.setProgressColor(0xffffffff);
        avatarProgressView.setNoProgress(false);
        frameLayout.addView(avatarProgressView, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 8));

        showAvatarProgress(false, false);

        nameTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT, false);
        nameTextView.setHint(getString("GroupName", R.string.GroupName));
        nameTextView.setFocusable(nameTextView.isEnabled());
        nameTextView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (avatarImage != null) {
                    avatarImage.invalidate();
                }
            }
        });
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(128);
        nameTextView.setFilters(inputFilters);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 5 : 96, 0, LocaleController.isRTL ? 96 : 5, 0));

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, dp(56));
        doneButton.setContentDescription(getString("Done", R.string.Done));

        nameTextView.setText(Emoji.replaceEmoji(encryptedGroup.getName(), nameTextView.getEditText().getPaint().getFontMetricsInt(), true));
        nameTextView.setSelection(nameTextView.length());

        settingsContainer = new LinearLayout(context);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(settingsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setAvatarCell = new TextCell(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        };
        setAvatarCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        setAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
        setAvatarCell.setOnClickListener(v -> {
            if (encryptedGroup.getState() != EncryptedGroupState.INITIALIZED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(getString(R.string.EncryptedGroupAvatarChangeForbidden));
                builder.setTitle(getString(R.string.ChatSetNewPhoto));
                builder.setNeutralButton(getString(R.string.OK), null);
                showDialog(builder.create());
                return;
            }
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            Intent chooserIntent = Intent.createChooser(photoPickerIntent, null);
            startActivityForResult(chooserIntent, LaunchActivity.SELECT_ENCRYPTED_GROUP_AVATAR);

            showAvatarProgress(false, true);
            cameraDrawable.setCurrentFrame(0);
            cameraDrawable.setCustomEndFrame(43);
            setAvatarCell.imageView.playAnimation();
        });
        settingsContainer.addView(setAvatarCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        deleteAvatarCell = new TextCell(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        };
        deleteAvatarCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        deleteAvatarCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
        deleteAvatarCell.setOnClickListener(v -> {
            if (encryptedGroup.getState() != EncryptedGroupState.INITIALIZED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(getString(R.string.EncryptedGroupAvatarChangeForbidden));
                builder.setTitle(getString(R.string.DeletePhoto));
                builder.setNeutralButton(getString(R.string.OK), null);
                showDialog(builder.create());
                return;
            }
            encryptedGroup.setAvatar(null);
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            new EncryptedGroupProtocol(currentAccount).deleteAvatar(encryptedGroup);
            setAvatar();
        });
        settingsContainer.addView(deleteAvatarCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.addView(new ShadowSectionCell(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
        listView.setAdapter(listAdapter = new ListAdapter(context));
        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (!view.isEnabled()) {
                return;
            }
            if (position == addMemberRow) {
                EncryptedGroupUtils.showNotImplementedDialog(this);
            } else if (firstMemberRow <= position && position <= lastMemberRow) {
                Bundle args = new Bundle();
                InnerEncryptedChat innerChat = getInnerChat(position - firstMemberRow);
                TLRPC.User user = getMessagesController().getUser(innerChat.getUserId());
                if (user == null) {
                    return;
                }
                args.putLong("user_id", user.id);
                args.putLong("dialog_id", innerChat.getDialogId().orElse(0L));
                args.putBoolean("reportSpam", false);
                args.putInt("actionBarColor", getThemedColor(Theme.key_actionBarDefault));
                ProfileActivity fragment = new ProfileActivity(args);
                presentFragment(fragment);
            }
        });

        setAvatar();

        return fragmentView;
    }

    @Override
    public boolean allowShowing() {
        return !FakePasscodeUtils.isFakePasscodeActivated();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (nameTextView != null) {
            nameTextView.onResume();
            nameTextView.getEditText().requestFocus();
        }
        if (!allowShowing()) {
            finishHiddenChatFragment();
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (nameTextView != null) {
            nameTextView.onPause();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (nameTextView != null && nameTextView.isPopupShowing()) {
            nameTextView.hidePopup(true);
            return false;
        }
        return checkDiscard();
    }

    private boolean checkDiscard() {
        if (nameTextView != null && !encryptedGroup.getName().equals(nameTextView.getText().toString())) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges))
                    .setMessage(getString(R.string.GroupSettingsChangedAlert))
                    .setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> processDone())
                    .setNegativeButton(getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment())
                    .create());
            return false;
        }
        return true;
    }

    private boolean createMenuForParticipant(InnerEncryptedChat innerChat, EncryptedGroupMemberCell cell) {
        ItemOptions.makeOptions(this, cell)
                .setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)))
                .add(R.drawable.msg_remove, getString(R.string.KickFromGroup), true, () ->
                        showRemoveMemberDialog(innerChat)
                )
                .setMinWidth(190)
                .show();

        return true;
    }

    private void showRemoveMemberDialog(InnerEncryptedChat innerChat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(getString(R.string.EncryptedGroupMemberRemovalConfirmation));
        builder.setTitle(getString(R.string.KickFromGroup));
        builder.setPositiveButton(getString(R.string.Delete), (dialogInterface, i) -> {
            long userId = innerChat.getUserId();
            new EncryptedGroupProtocol(currentAccount).kickMember(encryptedGroup, userId);
            membersCurrentlyRemoving.add(userId);
            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            showDialog(progressDialog);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.dialogsHidingChanged) {
            if (!allowShowing()) {
                finishHiddenChatFragment();
            }
        } else if (id == NotificationCenter.encryptedGroupMemberRemoved) {
            int encryptedGroupId = (int)args[0];
            if (encryptedGroupId == encryptedGroup.getInternalId()) {
                updateRows();
                listAdapter.notifyDataSetChanged();
                long userId = (long)args[1];
                if (membersCurrentlyRemoving.contains(userId)) {
                    TLRPC.User user = getMessagesController().getUser(userId);
                    if (user != null) {
                        BulletinFactory.createRemoveFromChatBulletin(this, user, encryptedGroup.getName()).show();
                    }
                    membersCurrentlyRemoving.remove(userId);
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            boolean avatarChanged = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0;
            if (avatarChanged) {
                EncryptedGroupUtils.applyAvatar(avatarImage, avatarDrawable, encryptedGroup);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || requestCode != LaunchActivity.SELECT_ENCRYPTED_GROUP_AVATAR || data == null || data.getData() == null) {
            return;
        }
        startCrop(data.getData());
    }

    private void startCrop(Uri uri) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                LaunchActivity activity = (LaunchActivity) getParentActivity();
                if (activity == null) {
                    return;
                }
                Bundle args = new Bundle();
                args.putParcelable("photoUri", uri);
                PhotoCropActivity photoCropActivity = new PhotoCropActivity(args);
                photoCropActivity.setDelegate(this);
                activity.presentFragment(photoCropActivity);
            } catch (Exception e) {
                PartisanLog.e(e);
                Bitmap bitmap = ImageLoader.loadBitmap(null, uri, 150, 150, true);
                processBitmap(bitmap);
            }
        });
    }

    private void finishHiddenChatFragment() {
        if (!finishing) {
            super.finishFragment(false);
        }
    }

    private void showAvatarProgress(boolean show, boolean animated) {
        if (avatarProgressView == null) {
            return;
        }
        if (avatarAnimation != null) {
            avatarAnimation.cancel();
            avatarAnimation = null;
        }
        if (animated) {
            avatarAnimation = new AnimatorSet();
            if (show) {
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setVisibility(View.VISIBLE);
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 1.0f));
            } else {
                avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(avatarOverlay, View.ALPHA, 0.0f));
            }
            avatarAnimation.setDuration(180);
            avatarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (avatarAnimation == null || avatarProgressView == null) {
                        return;
                    }
                    if (!show) {
                        avatarProgressView.setVisibility(View.INVISIBLE);
                        avatarOverlay.setVisibility(View.INVISIBLE);
                    }
                    avatarAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    avatarAnimation = null;
                }
            });
            avatarAnimation.start();
        } else {
            if (show) {
                avatarProgressView.setAlpha(1.0f);
                avatarProgressView.setVisibility(View.VISIBLE);
                avatarOverlay.setAlpha(1.0f);
                avatarOverlay.setVisibility(View.VISIBLE);
            } else {
                avatarProgressView.setAlpha(0.0f);
                avatarProgressView.setVisibility(View.INVISIBLE);
                avatarOverlay.setAlpha(0.0f);
                avatarOverlay.setVisibility(View.INVISIBLE);
            }
        }
    }

    RLottieDrawable cameraDrawable;

    private void setAvatar() {
        if (avatarImage == null) {
            return;
        }
        EncryptedGroupUtils.applyAvatar(avatarImage, avatarDrawable, encryptedGroup);
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().checkCurrentImageVisibility();
        }
        if (setAvatarCell != null) {
            if (encryptedGroup.hasAvatar()) {
                setAvatarCell.setTextAndIcon(getString(R.string.ChatSetNewPhoto), R.drawable.msg_addphoto, true);
            } else {
                setAvatarCell.setTextAndIcon(getString(R.string.ChatSetPhotoOrVideo), R.drawable.msg_addphoto, true);
            }
            if (cameraDrawable == null) {
                cameraDrawable = new RLottieDrawable(R.raw.camera_outline, "" + R.raw.camera_outline, dp(50), dp(50), false, null);
            }
            setAvatarCell.imageView.setTranslationX(-dp(8));
            setAvatarCell.imageView.setAnimation(cameraDrawable);
        }
        if (deleteAvatarCell != null) {
            if (encryptedGroup.hasAvatar()) {
                deleteAvatarCell.setVisibility(View.VISIBLE);
                deleteAvatarCell.setTextAndIcon(getString(R.string.DeletePhoto), R.drawable.msg_delete, true);
            } else {
                deleteAvatarCell.setVisibility(View.GONE);
            }
        }
    }

    private void processDone() {
        if (donePressed || nameTextView == null) {
            return;
        }
        if (nameTextView.length() == 0) {
            Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(nameTextView);
            return;
        }
        donePressed = true;

        ChangeGroupInfoAction action = new ChangeGroupInfoAction();
        if (!encryptedGroup.getName().equals(nameTextView.getText().toString())) {
            encryptedGroup.setName(nameTextView.getText().toString());
            action.name = encryptedGroup.getName();
            action.flags |= ChangeGroupInfoAction.FLAG_NAME;
        }

        if (action.flags != 0) {
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            new EncryptedGroupProtocol(currentAccount).sendActionToAllMembers(encryptedGroup, action, true);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        }
        finishFragment();
    }

    private void updateRows() {
        rowCount = 0;

        addMemberRow = rowCount++;
        selfRow = rowCount++;
        firstMemberRow = rowCount;
        lastMemberRow = firstMemberRow + encryptedGroup.getInnerChats().size() - 1;
        rowCount = lastMemberRow + 1;
    }

    private InnerEncryptedChat getInnerChat(int index) {
        return encryptedGroup.getInnerChats().get(index);
    }

    @Override
    public void didFinishEdit(Bitmap bitmap) {
        processBitmap(bitmap);
    }

    private void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        TLRPC.PhotoSize smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 150, 150, 80, true, 150, 150);
        Bitmap resizedBitmap = BitmapFactory.decodeByteArray(smallPhoto.bytes, 0, smallPhoto.size);
        encryptedGroup.setAvatar(resizedBitmap);
        getMessagesStorage().updateEncryptedGroup(encryptedGroup);
        new EncryptedGroupProtocol(currentAccount).sendNewAvatar(encryptedGroup);
        setAvatar();
        AndroidUtilities.runOnUIThread(() ->
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR)
        );
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_ADD_MEMBER = 0,
                VIEW_TYPE_MEMBER = 1;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_ADD_MEMBER:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_MEMBER:
                default:
                    EncryptedGroupMemberCell manageChatUserCell = new EncryptedGroupMemberCell(mContext, encryptedGroup, currentAccount);
                    manageChatUserCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = manageChatUserCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_ADD_MEMBER:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    actionCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (position == addMemberRow) {
                        actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        actionCell.setText(getString(R.string.AddMember), null, R.drawable.msg_contact_add, true);
                    }
                    break;
                case VIEW_TYPE_MEMBER:
                    EncryptedGroupMemberCell cell = (EncryptedGroupMemberCell) holder.itemView;
                    cell.setTag(position);

                    if (position == selfRow) {
                        cell.setUserAndInnerChat(getUserConfig().getCurrentUser(), null, position != lastMemberRow);
                        cell.setNeedOptions(false, null);
                    } else if (firstMemberRow <= position && position <= lastMemberRow) {
                        InnerEncryptedChat innerChat = getInnerChat(position - firstMemberRow);
                        TLRPC.User user = getMessagesController().getUser(innerChat.getUserId());
                        if (user != null) {
                            cell.setUserAndInnerChat(user, innerChat, position != lastMemberRow);
                            cell.setNeedOptions(true, v -> createMenuForParticipant(innerChat, cell));
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == addMemberRow) {
                return VIEW_TYPE_ADD_MEMBER;
            } else if (position == selfRow || firstMemberRow <= position && position <= lastMemberRow) {
                return VIEW_TYPE_MEMBER;
            }
            return VIEW_TYPE_MEMBER;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(settingsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(setAvatarCell, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(setAvatarCell, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(setAvatarCell, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        return themeDescriptions;
    }
}
