package org.telegram.messenger.partisan.secretgroups.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.AccountActions;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.fakepasscode.SelectionMode;
import org.telegram.messenger.partisan.appmigration.MaskedMigrationIssue;
import org.telegram.messenger.partisan.appmigration.MaskedMigratorHelper;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupProtocol;
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
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;
import org.telegram.ui.FakePasscodeAccountActionsActivity;
import org.telegram.ui.FakePasscodeActivationMethodsActivity;
import org.telegram.ui.FakePasscodeActivity;
import org.telegram.ui.FakePasscodeRemovePasscodesActivity;
import org.telegram.ui.FakePasscodeSmsActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EncryptedGroupEditActivity  extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AllowShowingActivityInterface {
    private int addMemberRow;
    private int selfRow;
    private int firstMemberRow;
    private int lastMemberRow;
    private int rowCount;

    private View doneButton;

    private LinearLayout avatarContainer;
    private BackupImageView avatarImage;
    private View avatarOverlay;
    private AnimatorSet avatarAnimation;
    private RadialProgressView avatarProgressView;
    private AvatarDrawable avatarDrawable;
    private EditTextEmoji nameTextView;

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
        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);

        getNotificationCenter().addObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.encryptedGroupMemberRemoved);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.encryptedGroupMemberRemoved);
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

        actionBar.setTitle(encryptedGroup.getName());

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
        builder.setMessage(LocaleController.getString(R.string.EncryptedGroupMemberRemovalConfirmation));
        builder.setTitle(LocaleController.getString(R.string.KickFromGroup));
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
            long userId = innerChat.getUserId();
            new EncryptedGroupProtocol(currentAccount).kickMember(encryptedGroup, userId);
            membersCurrentlyRemoving.add(userId);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
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
                }
            }
        }
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

    private void setAvatar() {
        if (avatarImage == null) {
            return;
        }
        avatarImage.setImageDrawable(avatarDrawable);
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().checkCurrentImageVisibility();
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
            action.flags |= 8;
        }

        if (action.flags != 0) {
            getMessagesStorage().updateEncryptedGroup(encryptedGroup);
            new EncryptedGroupProtocol(currentAccount).sendActionToAllMembers(encryptedGroup, action);
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

        return themeDescriptions;
    }
}
