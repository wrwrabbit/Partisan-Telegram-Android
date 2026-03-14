/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilesMigrationService;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SavedChannelsAdapter;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SavedChannelCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsItemAnimator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SavedChannelsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout viewPage;

    private DialogsRecyclerView listView;
    private LinearLayoutManager layoutManager;
    private SavedChannelsAdapter chatsAdapter;
    private ItemTouchHelper itemTouchhelper;
    private RecyclerAnimationScrollHelper scrollHelper;
    private FlickerLoadingView progressView;
    private int lastItemsCount;
    private DialogsItemAnimator dialogsItemAnimator;
    private RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;

    private final UndoView[] undoView = new UndoView[2];

    private View blurredView;

    private SavedChannelCell movingView;
    private boolean allowMoving;

    private final Paint actionBarDefaultPaint = new Paint();

    private NumberTextView selectedDialogsCountTextView;
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem pinItem;

    private float additionalFloatingTranslation;
    private float additionalFloatingTranslation2;

    private FragmentContextView fragmentLocationContextView;
    private FragmentContextView fragmentContextView;

    private int dialogRemoveFinished;
    private int dialogInsertFinished;
    private int dialogChangeFinished;

    private int currentConnectionState;

    private long openedDialogId;

    private RadialProgress2 updateLayoutIcon;
    private TextView updateTextView;

    private SavedChannelsActivityDelegate delegate;

    private int canPinCount;

    private int topPadding;

    private final static int pin = 100;
    private final static int delete = 102;

    private float progressToActionMode;
    private ValueAnimator actionBarColorAnimator;

    private float tabsYOffset;
    private float scrollAdditionalOffset;

    private final Property<SavedChannelsActivity, Float> SCROLL_Y = new AnimationProperties.FloatProperty<SavedChannelsActivity>("animationValue") {
        @Override
        public void setValue(SavedChannelsActivity object, float value) {
            object.setScrollY(value);
        }

        @Override
        public Float get(SavedChannelsActivity object) {
            return actionBar.getTranslationY();
        }
    };

    private class ContentView extends SizeNotifierFrameLayout {

        private final Paint actionBarSearchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ContentView(Context context) {
            super(context);
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            topPadding = top;
            updateContextViewPosition();
            requestLayout();
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == fragmentContextView && fragmentContextView.isCallStyle()) {
                return true;
            }
            if (child == blurredView) {
                return true;
            }
            boolean result;
            if (child == viewPage || child == fragmentContextView || child == fragmentLocationContextView) {
                canvas.save();
                canvas.clipRect(0, -getY() + actionBar.getY() + actionBar.getHeight(), getMeasuredWidth(), getMeasuredHeight());
                if (slideFragmentProgress != 1f) {
                    float s = 1f - 0.05f * (1f - slideFragmentProgress);
                    canvas.translate((isDrawerTransition ? AndroidUtilities.dp(4) : -AndroidUtilities.dp(4)) * (1f - slideFragmentProgress), 0);
                    canvas.scale(s, s, isDrawerTransition ? getMeasuredWidth() : 0, -getY() + actionBar.getY() + actionBar.getHeight());
                }

                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            } else if (child == actionBar && slideFragmentProgress != 1f) {
                canvas.save();
                float s = 1f - 0.05f * (1f - slideFragmentProgress);
                canvas.translate((isDrawerTransition ? AndroidUtilities.dp(4) : -AndroidUtilities.dp(4)) * (1f - slideFragmentProgress), 0);
                canvas.scale(s, s, isDrawerTransition ? getMeasuredWidth() : 0, (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2f);
                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            } else {
                result = super.drawChild(canvas, child, drawingTime);
            }
            if (child == actionBar && parentLayout != null) {
                int y = (int) (actionBar.getY() + actionBar.getHeight());
                parentLayout.drawHeaderShadow(canvas, 255, y);
            }
            return result;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int actionBarHeight = actionBar.getHeight();
            int top = (int) (-getY() + actionBar.getY());
            if (progressToActionMode > 0) {
                actionBarSearchPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault), Theme.getColor(Theme.key_windowBackgroundWhite), progressToActionMode));
                canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarSearchPaint);
            } else {
                canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarDefaultPaint);
            }
            tabsYOffset = 0;
            updateContextViewPosition();
            super.dispatchDraw(canvas);
            if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
                canvas.save();
                canvas.translate(fragmentContextView.getX(), fragmentContextView.getY());
                if (slideFragmentProgress != 1f) {
                    float s = 1f - 0.05f * (1f - slideFragmentProgress);
                    canvas.translate((isDrawerTransition ? AndroidUtilities.dp(4) : -AndroidUtilities.dp(4)) * (1f - slideFragmentProgress), 0);
                    canvas.scale(s, 1f, isDrawerTransition ? getMeasuredWidth() : 0, fragmentContextView.getY());
                }
                fragmentContextView.setDrawOverlay(true);
                fragmentContextView.draw(canvas);
                fragmentContextView.setDrawOverlay(false);
                canvas.restore();
            }
            if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
                if (blurredView.getAlpha() != 1f) {
                    if (blurredView.getAlpha() != 0) {
                        canvas.saveLayerAlpha(blurredView.getLeft(), blurredView.getTop(), blurredView.getRight(), blurredView.getBottom(), (int) (255 * blurredView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                        canvas.translate(blurredView.getLeft(), blurredView.getTop());
                        blurredView.draw(canvas);
                        canvas.restore();
                    }
                } else {
                    blurredView.draw(canvas);
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);
            heightSize -= getPaddingTop();

            measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

            int keyboardSize = measureKeyboardHeight();
            int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE || child == actionBar) {
                    continue;
                }
                if (child instanceof DatabaseMigrationHint) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int h = View.MeasureSpec.getSize(heightMeasureSpec) + keyboardSize;
                    int contentHeightSpec = View.MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), h + AndroidUtilities.dp(2) - actionBar.getMeasuredHeight()), View.MeasureSpec.EXACTLY);
                    child.measure(contentWidthSpec, contentHeightSpec);
                } else if (child == viewPage) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int h = heightSize + AndroidUtilities.dp(2) - (actionBar.getMeasuredHeight()) - topPadding;

                    child.setTranslationY(0);
                    int transitionPadding = (isSlideBackTransition || isDrawerTransition) ? (int) (h * 0.05f) : 0;
                    h += transitionPadding;
                    child.setPadding(child.getPaddingLeft(), child.getPaddingTop(), child.getPaddingRight(), transitionPadding);
                    child.measure(contentWidthSpec, View.MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), h), View.MeasureSpec.EXACTLY));
                    child.setPivotX(child.getMeasuredWidth() / 2);
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            setBottomClip(0);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

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
                        childTop = (b - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = (b - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }

                if (child instanceof DatabaseMigrationHint) {
                    childTop = actionBar.getMeasuredHeight();
                } else if (child == viewPage) {
                    childTop = actionBar.getMeasuredHeight();
                    childTop += topPadding;
                } else if (child instanceof FragmentContextView) {
                    childTop += actionBar.getMeasuredHeight();
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
            updateContextViewPosition();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (actionBar.isActionModeShowed()) {
                    allowMoving = true;
                }
            }
            return onTouchEvent(ev);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

    }

    public static float viewOffset = 0.0f;

    public class DialogsRecyclerView extends RecyclerListView {

        private boolean firstLayout = true;
        private boolean ignoreLayout;
        private int lastListPadding;

        Paint paint = new Paint();
        RectF rectF = new RectF();

        public DialogsRecyclerView(Context context) {
            super(context);
        }

        @Override
        protected boolean updateEmptyViewAnimated() {
            return true;
        }

        public void setViewsOffset(float viewOffset) {
            SavedChannelsActivity.viewOffset = viewOffset;
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                getChildAt(i).setTranslationY(viewOffset);
            }

            if (selectorPosition != NO_POSITION) {
                View v = getLayoutManager().findViewByPosition(selectorPosition);
                if (v != null) {
                    selectorRect.set(v.getLeft(), (int) (v.getTop() + viewOffset), v.getRight(), (int) (v.getBottom() + viewOffset));
                    selectorDrawable.setBounds(selectorRect);
                }
            }
            invalidate();
        }

        public float getViewOffset() {
            return viewOffset;
        }

        @Override
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            child.setTranslationY(viewOffset);
            child.setTranslationX(0);
            child.setAlpha(1f);
        }

        @Override
        public void removeView(View view) {
            super.removeView(view);
            view.setTranslationY(0);
            view.setTranslationX(0);
            view.setAlpha(1f);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (drawMovingViewsOverlayed()) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                for (int i = 0; i < getChildCount(); i++) {
                    View view = getChildAt(i);

                    if ((view instanceof SavedChannelCell && ((SavedChannelCell) view).isMoving()) || (view instanceof SavedChannelsAdapter.LastEmptyView && ((SavedChannelsAdapter.LastEmptyView) view).moving)) {
                        if (view.getAlpha() != 1f) {
                            rectF.set(view.getX(), view.getY(), view.getX() + view.getMeasuredWidth(), view.getY() +  view.getMeasuredHeight());
                            canvas.saveLayerAlpha(rectF, (int) (255 * view.getAlpha()), Canvas.ALL_SAVE_FLAG);
                        } else {
                            canvas.save();
                        }
                        canvas.translate(view.getX(), view.getY());
                        canvas.drawRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), paint);
                        view.draw(canvas);
                        canvas.restore();
                    }
                }
                invalidate();
            }
        }

        private boolean drawMovingViewsOverlayed() {
            return getItemAnimator() != null && getItemAnimator().isRunning() && (dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0);
        }

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (drawMovingViewsOverlayed() && child instanceof SavedChannelCell && ((SavedChannelCell) child).isMoving()) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
        }

        @Override
        public void setAdapter(RecyclerView.Adapter adapter) {
            super.setAdapter(adapter);
            firstLayout = true;
        }

        private void checkIfAdapterValid() {
            RecyclerView.Adapter adapter = getAdapter();
            if (lastItemsCount != adapter.getItemCount()) {
                ignoreLayout = true;
                adapter.notifyDataSetChanged();
                ignoreLayout = false;
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int pos = layoutManager.findFirstVisibleItemPosition();
            if (pos != RecyclerView.NO_POSITION && itemTouchhelper.isIdle()) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(pos);
                if (holder != null) {
                    int top = holder.itemView.getTop();

                    ignoreLayout = true;
                    layoutManager.scrollToPositionWithOffset(pos, (int) (top - lastListPadding + scrollAdditionalOffset));
                    ignoreLayout = false;
                }
            }
            ignoreLayout = true;
            int t = 0;
            setTopGlowOffset(t);
            setPadding(0, t, 0, 0);
            progressView.setPaddingTop(t);
            ignoreLayout = false;

            if (firstLayout && getMessagesController().dialogsLoaded) {
                firstLayout = false;
            }
            checkIfAdapterValid();
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            lastListPadding = getPaddingTop();
            scrollAdditionalOffset = 0;

            if ((dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) && !dialogsItemAnimator.isRunning()) {
                onDialogAnimationFinished();
            }
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (fastScrollAnimationRunning || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
                return false;
            }
            int action = e.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            }
            return super.onTouchEvent(e);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (fastScrollAnimationRunning || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
                return false;
            }
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                checkIfAdapterValid();
            }
            return super.onInterceptTouchEvent(e);
        }
    }

    public interface SavedChannelsActivityDelegate {
        void didSelectDialogs(SavedChannelsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param);
    }

    private class SwipeController extends ItemTouchHelper.Callback {
        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (waitingForChatsAnimationEnd() || parentLayout != null && parentLayout.isInPreviewMode()) {
                return 0;
            }
            if (viewHolder.itemView instanceof SavedChannelCell) {
                SavedChannelCell savedChannelCell = (SavedChannelCell) viewHolder.itemView;
                String userName = savedChannelCell.getUserName();
                if (actionBar.isActionModeShowed(null)) {
                    if (!allowMoving || userName == null || !isPinned(userName)) {
                        return 0;
                    }
                    movingView = (SavedChannelCell) viewHolder.itemView;
                    movingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                }
            }
            return 0;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (!(target.itemView instanceof SavedChannelCell)) {
                return false;
            }
            SavedChannelCell savedChannelCell = (SavedChannelCell) target.itemView;
            String userName = savedChannelCell.getUserName();
            if (userName == null || !isPinned(userName)) {
                return false;
            }
            int fromIndex = source.getAdapterPosition();
            int toIndex = target.getAdapterPosition();
            UserConfig config = UserConfig.getInstance(currentAccount);
            Collections.swap(config.pinnedSavedChannels, fromIndex, toIndex);
            config.saveConfig(true);
            chatsAdapter.moveItem(fromIndex, toIndex);
            updateDialogIndices();
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) { }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder != null) {
                listView.hideSelector(false);
            }
            if (viewHolder != null && viewHolder.itemView instanceof SavedChannelCell) {
                ((SavedChannelCell) viewHolder.itemView).swipeCanceled = false;
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public long getAnimationDuration(RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
                return 200;
            } else if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
                if (movingView != null) {
                    View view = movingView;
                    AndroidUtilities.runOnUIThread(() -> view.setBackgroundDrawable(null), dialogsItemAnimator.getMoveDuration());
                    movingView = null;
                }
            }
            return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy);
        }

        @Override
        public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
            return 0.45f;
        }

        @Override
        public float getSwipeEscapeVelocity(float defaultValue) {
            return 3500;
        }

        @Override
        public float getSwipeVelocityThreshold(float defaultValue) {
            return Float.MAX_VALUE;
        }
    }

    public SavedChannelsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        currentConnectionState = getConnectionsManager().getConnectionState();

        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeSearchByActiveAction);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.openedChatChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().addObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().addObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().addObserver(this, NotificationCenter.needDeleteDialog);
        getNotificationCenter().addObserver(this, NotificationCenter.newSuggestionsAvailable);
        getNotificationCenter().addObserver(this, NotificationCenter.onDatabaseMigration);
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);

        getMessagesController().loadPinnedDialogs(0, 0, null);
        if (databaseMigrationHint != null && !getMessagesStorage().isDatabaseMigrationInProgress()) {
            View localView = databaseMigrationHint;
            if (localView.getParent() != null) {
                ((ViewGroup) localView.getParent()).removeView(localView);
            }
            databaseMigrationHint = null;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeSearchByActiveAction);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.openedChatChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().removeObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().removeObserver(this, NotificationCenter.needDeleteDialog);
        getNotificationCenter().removeObserver(this, NotificationCenter.newSuggestionsAvailable);

        getNotificationCenter().removeObserver(this, NotificationCenter.onDatabaseMigration);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        int animationIndex = -1;
        getNotificationCenter().onAnimationFinish(animationIndex);
        delegate = null;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {
            @Override
            public void setTranslationY(float translationY) {
                if (translationY != getTranslationY() && fragmentView != null) {
                    fragmentView.invalidate();
                }
                super.setTranslationY(translationY);
            }
        };
        actionBar.setUseContainerForTitles();
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), true);
        return actionBar;
    }

    @Override
    public View createView(final Context context) {
        AndroidUtilities.runOnUIThread(() -> Theme.createChatResources(context, false));

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrOpenMenu", R.string.AccDescrOpenMenu));
        if (BuildVars.DEBUG_VERSION) {
            actionBar.setTitle("Telegram Beta");
        } else {
            actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
        }
        actionBar.setSupportsHolidayImage(true);
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);
        actionBar.setTitleActionRunnable(this::scrollToTop);

        actionBar.setAllowOverlayTitle(true);

        createActionMode();

        ContentView contentView = new ContentView(context);
        fragmentView = contentView;

        viewPage = new FrameLayout(context);
        contentView.addView(viewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new FlickerLoadingView(context);
        progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        progressView.setVisibility(View.GONE);
        viewPage.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        listView = new DialogsRecyclerView(context);
        listView.setAccessibilityEnabled(false);
        listView.setAnimateEmptyView(true, 0);
        listView.setClipToPadding(false);
        listView.setPivotY(0);
        dialogsItemAnimator = new DialogsItemAnimator(listView) {
            @Override
            public void onRemoveStarting(RecyclerView.ViewHolder item) {
                super.onRemoveStarting(item);
                if (layoutManager.findFirstVisibleItemPosition() == 0) {
                    View v = layoutManager.findViewByPosition(0);
                    if (v != null) {
                        v.invalidate();
                    }
                }
            }

            @Override
            public void onRemoveFinished(RecyclerView.ViewHolder item) {
                if (dialogRemoveFinished == 2) {
                    dialogRemoveFinished = 1;
                }
            }

            @Override
            public void onAddFinished(RecyclerView.ViewHolder item) {
                if (dialogInsertFinished == 2) {
                    dialogInsertFinished = 1;
                }
            }

            @Override
            public void onChangeFinished(RecyclerView.ViewHolder item, boolean oldItem) {
                if (dialogChangeFinished == 2) {
                    dialogChangeFinished = 1;
                }
            }

            @Override
            protected void onAllAnimationsDone() {
                if (dialogRemoveFinished == 1 || dialogInsertFinished == 1 || dialogChangeFinished == 1) {
                    onDialogAnimationFinished();
                }
            }
        };
        listView.setItemAnimator(dialogsItemAnimator);
        listView.setVerticalScrollBarEnabled(true);
        listView.setInstantClick(true);
        layoutManager = new LinearLayoutManager(context) {

            private boolean fixOffset;

            @Override
            public void scrollToPositionWithOffset(int position, int offset) {
                if (fixOffset) {
                    offset -= listView.getPaddingTop();
                }
                super.scrollToPositionWithOffset(position, offset);
            }

            @Override
            public void prepareForDrop(@NonNull View view, @NonNull View target, int x, int y) {
                fixOffset = true;
                super.prepareForDrop(view, target, x, y);
                fixOffset = false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE);
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (listView.fastScrollAnimationRunning) {
                    return 0;
                }
                boolean isDragging = listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;

                int measuredDy = dy;
                if (listView.getViewOffset() != 0 && dy > 0 && isDragging) {
                    float ty = (int) listView.getViewOffset();
                    ty -= dy;
                    if (ty < 0) {
                        measuredDy = (int) ty;
                        ty = 0;
                    } else {
                        measuredDy = 0;
                    }
                    listView.setViewsOffset(ty);
                }
                try {
                    return super.scrollVerticallyBy(measuredDy, recycler, state);
                } catch (Exception ignore) {
                    return 0;
                }
            }

            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    try {
                        super.onLayoutChildren(recycler, state);
                    } catch (IndexOutOfBoundsException e) {
                        throw new RuntimeException("Inconsistency detected.");
                    }
                } else {
                    try {
                        super.onLayoutChildren(recycler, state);
                    } catch (IndexOutOfBoundsException e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> chatsAdapter.notifyDataSetChanged());
                    }
                }
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        viewPage.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> onItemClick(view, position, chatsAdapter));
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                return onItemLongClick(view, position, x, y);
            }

            @Override
            public void onLongClickRelease() {
                finishPreviewFragment();
            }

            @Override
            public void onMove(float dx, float dy) {
                movePreviewFragment(dy);
            }
        });
        SwipeController swipeController = new SwipeController();
        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(listView, false);

        itemTouchhelper = new ItemTouchHelper(swipeController);
        itemTouchhelper.attachToRecyclerView(listView);

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                dialogsItemAnimator.onListScroll(-dy);
                checkListLoad();
            }
        });

        chatsAdapter = new SavedChannelsAdapter(context, currentAccount) {
            @Override
            public void notifyDataSetChanged() {
                lastItemsCount = getItemCount();
                try {
                    super.notifyDataSetChanged();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        };
        chatsAdapter.loadChats();
        if (AndroidUtilities.isTablet() && openedDialogId != 0) {
            chatsAdapter.setOpenedDialogId(openedDialogId);
        }
        listView.setAdapter(chatsAdapter);

        listView.setEmptyView(progressView);
        scrollHelper = new RecyclerAnimationScrollHelper(listView, layoutManager);

        fragmentLocationContextView = new FragmentContextView(context, this, true);
        fragmentLocationContextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
        contentView.addView(fragmentLocationContextView);

        fragmentContextView = new FragmentContextView(context, this, false);
        fragmentContextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
        contentView.addView(fragmentContextView);

        fragmentContextView.setAdditionalContextView(fragmentLocationContextView);
        fragmentLocationContextView.setAdditionalContextView(fragmentContextView);

        final FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        contentView.addView(actionBar, layoutParams);

        FrameLayout updateLayout = new FrameLayout(context) {

            private final Paint paint = new Paint();
            private final Matrix matrix = new Matrix();
            private LinearGradient updateGradient;
            private int lastGradientWidth;

            @Override
            protected void onDraw(Canvas canvas) {
                if (updateGradient == null) {
                    return;
                }
                paint.setColor(0xffffffff);
                paint.setShader(updateGradient);
                updateGradient.setLocalMatrix(matrix);
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
                updateLayoutIcon.setBackgroundGradientDrawable(updateGradient);
                updateLayoutIcon.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (lastGradientWidth != width) {
                    updateGradient = new LinearGradient(0, 0, width, 0, new int[]{0xff69BF72, 0xff53B3AD}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
                    lastGradientWidth = width;
                }
                int x = (getMeasuredWidth() - updateTextView.getMeasuredWidth()) / 2;
                updateLayoutIcon.setProgressRect(x, AndroidUtilities.dp(13), x + AndroidUtilities.dp(22), AndroidUtilities.dp(13 + 22));
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                additionalFloatingTranslation2 = AndroidUtilities.dp(48) - translationY;
                if (additionalFloatingTranslation2 < 0) {
                    additionalFloatingTranslation2 = 0;
                }
            }
        };
        updateLayout.setWillNotDraw(false);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(AndroidUtilities.dp(48));
        if (Build.VERSION.SDK_INT >= 21) {
            updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        }
        contentView.addView(updateLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> {
            if (!SharedConfig.isAppUpdateAvailable()) {
                return;
            }
            AndroidUtilities.openForView(SharedConfig.pendingPtgAppUpdate.document, true, getParentActivity());
        });

        updateLayoutIcon = new RadialProgress2(updateLayout);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff);
        updateLayoutIcon.setCircleRadius(AndroidUtilities.dp(11));
        updateLayoutIcon.setAsMini();
        updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, false);

        updateTextView = new TextView(context);
        updateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        updateTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        updateTextView.setText(LocaleController.getString("AppUpdateNow", R.string.AppUpdateNow).toUpperCase());
        updateTextView.setTextColor(0xffffffff);
        updateTextView.setPadding(AndroidUtilities.dp(30), 0, 0, 0);
        updateLayout.addView(updateTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context) {
                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    if (this == undoView[0] && undoView[1].getVisibility() != VISIBLE) {
                        additionalFloatingTranslation = getMeasuredHeight() + AndroidUtilities.dp(8) - translationY;
                        if (additionalFloatingTranslation < 0) {
                            additionalFloatingTranslation = 0;
                        }
                    }
                }

                @Override
                protected boolean canUndo() {
                    return !dialogsItemAnimator.isRunning();
                }
            };
            contentView.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        blurredView.setVisibility(View.GONE);
        contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBarDefaultPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FilesMigrationService.checkBottomSheet(this);
        }

        return fragmentView;
    }

    private void updateContextViewPosition() {
        float filtersTabsHeight = 0;
        if (fragmentContextView != null) {
            float from = 0;
            if (fragmentLocationContextView != null && fragmentLocationContextView.getVisibility() == View.VISIBLE) {
                from += AndroidUtilities.dp(36);
            }
            fragmentContextView.setTranslationY(from + fragmentContextView.getTopPadding() + actionBar.getTranslationY() + filtersTabsHeight + tabsYOffset);
        }
        if (fragmentLocationContextView != null) {
            float from = 0;
            if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                from += AndroidUtilities.dp(fragmentContextView.getStyleHeight()) + fragmentContextView.getTopPadding();
            }
            fragmentLocationContextView.setTranslationY(from + fragmentLocationContextView.getTopPadding() + actionBar.getTranslationY() + filtersTabsHeight + tabsYOffset);
        }
    }

    private void createActionMode() {
        if (actionBar.actionModeIsExist(null)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);
        actionMode.setBackground(null);

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        pinItem = actionMode.addItemWithWidth(pin, R.drawable.msg_pin, AndroidUtilities.dp(54));
        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));

        actionModeViews.add(pinItem);
        actionModeViews.add(deleteItem);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode();
                    } else {
                        finishFragment();
                    }
                } else if (id >= 10 && id < 10 + UserConfig.MAX_ACCOUNT_COUNT) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    SavedChannelsActivityDelegate oldDelegate = delegate;
                    LaunchActivity launchActivity = (LaunchActivity) getParentActivity();
                    launchActivity.switchToAccount(id - 10, true);

                    SavedChannelsActivity SavedChannelsActivity = new SavedChannelsActivity(arguments);
                    SavedChannelsActivity.setDelegate(oldDelegate);
                    launchActivity.presentFragment(SavedChannelsActivity, false, true);
                } else if (id == pin) {
                    List<String> selectedUsernames = chatsAdapter.getSelectedUserNames();
                    UserConfig userConfig = getUserConfig();
                    for (String userName : selectedUsernames) {
                        if (canPinCount != 0) {
                            if (isPinned(userName)) {
                                continue;
                            }
                            pinDialog(userName, true, true);

                        } else {
                            if (!isPinned(userName)) {
                                continue;
                            }
                            pinDialog(userName, false, true);
                        }
                    }
                    userConfig.saveConfig(true);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode();
                    }
                } else if (id == delete) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.formatString("DeleteFewChatsTitle", R.string.DeleteFewChatsTitle, LocaleController.formatPluralString("ChatsSelected", chatsAdapter.getSelectedDialogCount())));
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteFewChats", R.string.AreYouSureDeleteFewChats));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog1, which) -> {
                        List<String> selectedUsernames = chatsAdapter.getSelectedUserNames();
                        UserConfig userConfig = getUserConfig();
                        userConfig.savedChannels.removeAll(selectedUsernames);
                        userConfig.pinnedSavedChannels.removeAll(selectedUsernames);
                        userConfig.saveConfig(true);
                        chatsAdapter.removeItems(selectedUsernames);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        if (actionBar.isActionModeShowed()) {
                            hideActionMode();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                }
            }
        });
    }

    @Override
    protected void onPanTranslationUpdate(float y) {
        if (viewPage == null) {
            return;
        }
        viewPage.setTranslationY(y);
        actionBar.setTranslationY(y);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!parentLayout.isInPreviewMode() && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
        if (viewPage != null) {
            chatsAdapter.notifyDataSetChanged();
        }
        getMediaDataController().checkStickers(MediaDataController.TYPE_EMOJI);
        if (XiaomiUtilities.isMIUI() && Build.VERSION.SDK_INT >= 19 && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
            if (getParentActivity() == null) {
                return;
            }
            if (MessagesController.getGlobalNotificationsSettings().getBoolean("askedAboutMiuiLockscreen", false)) {
                return;
            }
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(LocaleController.getString("AppName", R.string.AppName))
                    .setMessage(LocaleController.getString("PermissionXiaomiLockscreen", R.string.PermissionXiaomiLockscreen))
                    .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
                        Intent intent = XiaomiUtilities.getPermissionManagerIntent();
                        try {
                            getParentActivity().startActivity(intent);
                        } catch (Exception x) {
                            try {
                                intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception xx) {
                                FileLog.e(xx);
                            }
                        }
                    })
                    .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), (dialog, which) -> MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askedAboutMiuiLockscreen", true).commit())
                    .create());
        }
        showNextSupportedSuggestion();
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public void onBottomOffsetChange(float offset) {
                if (undoView[0] != null && undoView[0].getVisibility() == View.VISIBLE) {
                    return;
                }
                additionalFloatingTranslation = offset;
                if (additionalFloatingTranslation < 0) {
                    additionalFloatingTranslation = 0;
                }
            }

            @Override
            public void onShow(Bulletin bulletin) {
                if (undoView[0] != null && undoView[0].getVisibility() == View.VISIBLE) {
                    undoView[0].hide(true, 2);
                }
            }
        });
        updateVisibleRows(0, false);

    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        Bulletin.removeDelegate(this);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (actionBar != null && actionBar.isActionModeShowed()) {
            if (invoked) hideActionMode();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
    }

    public boolean addOrRemoveSelectedDialog(long did, View cell) {
        if (chatsAdapter.addOrRemoveSelectedDialog(did)) {
            if (cell instanceof SavedChannelCell) {
                ((SavedChannelCell) cell).setChecked(true, true);
            }
            return true;
        } else {
            if (cell instanceof SavedChannelCell) {
                ((SavedChannelCell) cell).setChecked(false, true);
            }
            return false;
        }
    }

    private void checkListLoad() {
        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
        int visibleItemCount = Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        boolean load = false;
        boolean loadFromCache = false;
        if (visibleItemCount > 0 && lastVisibleItem >= chatsAdapter.getChatsArray().size() - 10) {
            loadFromCache = !getMessagesController().isDialogsEndReached(0);
            if (loadFromCache || !getMessagesController().isServerDialogsEndReached(0)) {
                load = true;
            }
        }
        if (load) {
            boolean loadFromCacheFinal = loadFromCache;
            AndroidUtilities.runOnUIThread(() -> getMessagesController().loadDialogs(0, -1, 100, loadFromCacheFinal));
        }
    }

    private void onItemClick(View view, int position, RecyclerListView.Adapter adapter) {
        if (getParentActivity() == null) {
            return;
        }
        long dialogId = 0;
        int message_id = 0;
        if (adapter instanceof SavedChannelsAdapter) {
            SavedChannelsAdapter dialogsAdapter = (SavedChannelsAdapter) adapter;
            TLObject object = dialogsAdapter.getItem(position);
            if (object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                dialogId = -chat.id;
                if (actionBar.isActionModeShowed(null)) {
                    showOrUpdateActionMode(dialogId, view);
                    return;
                }
            } else {
                return;
            }
        }

        if (dialogId == 0) {
            return;
        }

        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        args.putBoolean("is_saved_channel", true);
        if (AndroidUtilities.isTablet()) {
            if (openedDialogId == dialogId) {
                return;
            }
            if (viewPage != null) {
                chatsAdapter.setOpenedDialogId(openedDialogId = dialogId);
            }
            updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
        }
        if (getMessagesController().checkCanOpenChat(args, SavedChannelsActivity.this)) {
            ChatActivity chatActivity = new ChatActivity(args);
            if (DialogObject.isUserDialog(dialogId) && (getMessagesController().dialogs_dict.get(dialogId) == null)) {
                TLRPC.Document sticker = getMediaDataController().getGreetingsSticker();
                if (sticker != null) {
                    chatActivity.setPreloadedSticker(sticker, true);
                }
            }
            presentFragment(chatActivity);
        }
    }

    private boolean onItemLongClick(View view, int position, float x, float y) {
        if (getParentActivity() == null) {
            return false;
        }
        if (!actionBar.isActionModeShowed() && !AndroidUtilities.isTablet() && view instanceof SavedChannelCell) {
            SavedChannelCell cell = (SavedChannelCell) view;
            if (cell.isPointInsideAvatar(x, y)) {
                return showChatPreview(cell);
            }
        }
        TLRPC.Chat chat;
        List<TLRPC.Chat> chats = chatsAdapter.getChatsArray();
        if (position < 0 || position >= chats.size()) {
            return false;
        }
        chat = chats.get(position);

        if (chat == null) {
            return false;
        }
        if (actionBar.isActionModeShowed() && isPinned(chat.username)) {
            return false;
        }

        showOrUpdateActionMode(-chat.id, view);
        return true;
    }

    private boolean showChatPreview(SavedChannelCell cell) {
        long dialogId = cell.getChatId();
        Bundle args = new Bundle();
        int message_id = cell.getMessageId();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        } else {
            if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                long did = dialogId;
                if (message_id != 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-did);
                    if (chat != null && chat.migrated_to != null) {
                        args.putLong("migrated_to", did);
                        did = -chat.migrated_to.channel_id;
                    }
                }
                args.putLong("chat_id", -did);
            }
        }
        if (message_id != 0) {
            args.putInt("message_id", message_id);
        }
        if (getMessagesController().checkCanOpenChat(args, SavedChannelsActivity.this)) {
            prepareBlurBitmap();
            presentFragmentAsPreview(new ChatActivity(args));
        }
        return true;
    }

    private boolean waitingForChatsAnimationEnd() {
        return dialogsItemAnimator.isRunning() || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0;
    }

    private void onDialogAnimationFinished() {
        dialogRemoveFinished = 0;
        dialogInsertFinished = 0;
        dialogChangeFinished = 0;
        AndroidUtilities.runOnUIThread(() -> {
            updateChatsIndices();
        });
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        updateContextViewPosition();
        if (viewPage != null) {
            listView.setTopGlowOffset(listView.getPaddingTop() + (int) value);
        }
        fragmentView.invalidate();
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
            } else {
                blurredView.setAlpha(progress);
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
    }

    private void resetScroll() {
        if (actionBar.getTranslationY() == 0) {
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, SCROLL_Y, 0));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    private void hideActionMode() {
        actionBar.hideActionMode();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        chatsAdapter.clearSelectedDialogs();
        if (actionBarColorAnimator != null) {
            actionBarColorAnimator.cancel();
        }
        actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 0);
        actionBarColorAnimator.addUpdateListener(valueAnimator -> {
            progressToActionMode = (float) valueAnimator.getAnimatedValue();
            for (int i = 0; i < actionBar.getChildCount(); i++) {
                if (actionBar.getChildAt(i).getVisibility() == View.VISIBLE && actionBar.getChildAt(i) != actionBar.getActionMode() && actionBar.getChildAt(i) != actionBar.getBackButton()) {
                    actionBar.getChildAt(i).setAlpha(1f - progressToActionMode);
                }
            }
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        });
        actionBarColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        actionBarColorAnimator.setDuration(200);
        actionBarColorAnimator.start();
        allowMoving = false;
        updateCounters(true);
        chatsAdapter.onReorderStateChanged(false);
        updateVisibleRows(MessagesController.UPDATE_MASK_REORDER | MessagesController.UPDATE_MASK_CHECK | MessagesController.UPDATE_MASK_CHAT);
    }

    private boolean isPinned(String userName) {
        return  UserConfig.getInstance(currentAccount).pinnedSavedChannels.contains(userName);
    }

    private void pinDialog(String userName, boolean pin, boolean animated) {
        if (pin) {
            getUserConfig().pinnedSavedChannels.add(userName);
        } else {
            getUserConfig().pinnedSavedChannels.remove(userName);
        }
        getUserConfig().saveConfig(true);
        int selectedChatIndex = -1;
        int currentChatIndex = -1;

        int scrollToPosition = 0;
        int currentPosition = layoutManager.findFirstVisibleItemPosition();

        boolean needScroll = false;
        if (currentPosition > scrollToPosition || !animated) {
            needScroll = true;
        }

        chatsAdapter.fixChatPosition(userName);

        if (needScroll) {
            scrollToTop();
        }
    }

    private void scrollToTop() {
        int scrollDistance = layoutManager.findFirstVisibleItemPosition() * AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
        if (scrollDistance >= listView.getMeasuredHeight() * 1.2f) {
            scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(0, 0, false, true);
            resetScroll();
        } else {
            listView.smoothScrollToPosition(0);
        }
    }

    private void updateCounters(boolean hide) {
        canPinCount = 0;
        if (hide) {
            return;
        }
        List<String> selectedUsernames = chatsAdapter.getSelectedUserNames();
        for (String userName : selectedUsernames) {
            boolean pinned = isPinned(userName);
            if (!pinned) {
                canPinCount++;
            }
        }
        pinItem.setVisibility(View.VISIBLE);
        if (canPinCount != 0) {
            pinItem.setIcon(R.drawable.msg_pin);
            pinItem.setContentDescription(LocaleController.getString("PinToTop", R.string.PinToTop));
        } else {
            pinItem.setIcon(R.drawable.msg_unpin);
            pinItem.setContentDescription(LocaleController.getString("UnpinFromTop", R.string.UnpinFromTop));
        }
        deleteItem.setVisibility(View.VISIBLE);
    }

    private void showOrUpdateActionMode(long dialogId, View cell) {
        addOrRemoveSelectedDialog(dialogId, cell);
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            if (chatsAdapter.getSelectedDialogCount() == 0) {
                hideActionMode();
                return;
            }
            updateAnimated = true;
        } else {
            createActionMode();
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            actionBar.showActionMode();
            actionBar.setBackButtonDrawable(new BackDrawable(true));
            resetScroll();

            AnimatorSet animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
                AndroidUtilities.clearDrawableAnimation(view);
                animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(200);
            animatorSet.start();

            if (!getUserConfig().pinnedSavedChannels.isEmpty()) {
                if (chatsAdapter != null) {
                    chatsAdapter.onReorderStateChanged(true);
                }
                updateVisibleRows(MessagesController.UPDATE_MASK_REORDER);
            }

            if (actionBarColorAnimator != null) {
                actionBarColorAnimator.cancel();
            }
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 1f);
            actionBarColorAnimator.addUpdateListener(valueAnimator -> {
                progressToActionMode = (float) valueAnimator.getAnimatedValue();
                for (int i = 0; i < actionBar.getChildCount(); i++) {
                    if (actionBar.getChildAt(i).getVisibility() == View.VISIBLE && actionBar.getChildAt(i) != actionBar.getActionMode() && actionBar.getChildAt(i) != actionBar.getBackButton()) {
                        actionBar.getChildAt(i).setAlpha(1f - progressToActionMode);
                    }
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            });
            actionBarColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            actionBarColorAnimator.setDuration(200);
            actionBarColorAnimator.start();
        }
        updateCounters(false);
        selectedDialogsCountTextView.setNumber(chatsAdapter.getSelectedDialogCount(), updateAnimated);
    }

    public UndoView getUndoView() {
        if (undoView[0].getVisibility() == View.VISIBLE) {
            UndoView old = undoView[0];
            undoView[0] = undoView[1];
            undoView[1] = old;
            old.hide(true, 2);
            ContentView contentView = (ContentView) fragmentView;
            contentView.removeView(undoView[0]);
            contentView.addView(undoView[0]);
        }
        return undoView[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (viewPage == null) {
                return;
            }

            if (viewPage.getVisibility() == View.VISIBLE) {
                int oldItemCount = chatsAdapter.getCurrentCount();

                if (chatsAdapter.isDataSetChanged() || args.length > 0) {
                    chatsAdapter.notifyDataSetChanged();
                    int newItemCount = chatsAdapter.getItemCount();
                    if (newItemCount > oldItemCount) {
                        recyclerItemsEnterAnimator.showItemsAnimated(oldItemCount);
                    }
                } else {
                    updateVisibleRows(MessagesController.UPDATE_MASK_NEW_MESSAGE);
                    int newItemCount = chatsAdapter.getItemCount();
                    if (newItemCount > oldItemCount) {
                        recyclerItemsEnterAnimator.showItemsAnimated(oldItemCount);
                    }
                }
                try {
                    listView.setEmptyView(progressView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                checkListLoad();
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer mask = (Integer) args[0];
            updateVisibleRows(mask);
        } else if (id == NotificationCenter.openedChatChanged) {
            if (viewPage == null) {
                return;
            }
            if (AndroidUtilities.isTablet()) {
                boolean close = (Boolean) args[2];
                long dialog_id = (Long) args[0];
                if (close) {
                    if (dialog_id == openedDialogId) {
                        openedDialogId = 0;
                    }
                } else {
                    openedDialogId = dialog_id;
                }
                chatsAdapter.setOpenedDialogId(openedDialogId);
            }
            updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.messageReceivedByAck || id == NotificationCenter.messageReceivedByServer || id == NotificationCenter.messageSendError) {
            updateVisibleRows(MessagesController.UPDATE_MASK_SEND_STATE);
        } else if (id == NotificationCenter.replyMessagesDidLoad) {
            updateVisibleRows(MessagesController.UPDATE_MASK_MESSAGE_TEXT);
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = AccountInstance.getInstance(account).getConnectionsManager().getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
            }
        } else if (id == NotificationCenter.needDeleteDialog) {
            if (fragmentView == null || isPaused) {
                return;
            }
            long dialogId = (Long) args[0];
            TLRPC.User user = (TLRPC.User) args[1];
            TLRPC.Chat chat = (TLRPC.Chat) args[2];
            boolean revoke = (Boolean) args[3];
            Runnable deleteRunnable = () -> {
                if (chat != null) {
                    if (ChatObject.isNotInChat(chat)) {
                        getMessagesController().deleteDialog(dialogId, 0, revoke);
                    } else {
                        getMessagesController().deleteParticipantFromChat(-dialogId, getMessagesController().getUser(getUserConfig().getClientUserId()), null, revoke, revoke);
                    }
                } else {
                    getMessagesController().deleteDialog(dialogId, 0, revoke);
                    if (user != null && user.bot) {
                        getMessagesController().blockPeer(user.id);
                    }
                }
                getMessagesController().checkIfFolderEmpty(0);
            };
            if (undoView[0] != null) {
                getUndoView().showWithAction(dialogId, UndoView.ACTION_DELETE, deleteRunnable);
            } else {
                deleteRunnable.run();
            }
        } else if (id == NotificationCenter.newSuggestionsAvailable) {
            showNextSupportedSuggestion();
        } else if (id == NotificationCenter.onDatabaseMigration) {
            boolean startMigration = (boolean) args[0];
            if (fragmentView != null) {
                if (startMigration) {
                    if (databaseMigrationHint == null) {
                        databaseMigrationHint = new DatabaseMigrationHint(fragmentView.getContext(), currentAccount);
                        databaseMigrationHint.setAlpha(0f);
                        ((ContentView) fragmentView).addView(databaseMigrationHint);
                        databaseMigrationHint.animate().alpha(1).setDuration(300).setStartDelay(1000).start();
                    }
                    databaseMigrationHint.setTag(1);
                } else {
                    if (databaseMigrationHint != null && databaseMigrationHint.getTag() != null) {
                        View localView = databaseMigrationHint;
                        localView.animate().setListener(null).cancel();
                        localView.animate().setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (localView.getParent() != null) {
                                    ((ViewGroup) localView.getParent()).removeView(localView);
                                }
                                databaseMigrationHint = null;
                            }
                        }).alpha(0f).setStartDelay(0).setDuration(150).start();
                        databaseMigrationHint.setTag(null);
                    }
                }
            }
        } else if (id == NotificationCenter.messagesDidLoad) {
            chatsAdapter.messagesDidLoad((long)args[0], (List<MessageObject>)args[2]);
        }
    }

    View databaseMigrationHint;

    private boolean versionGreater(int major, int minor, int patch, int otherMajor, int otherMinor, int otherPatch) {
        return major > otherMajor || major == otherMajor && minor > otherMinor
                || major == otherMajor && minor == otherMinor && patch > otherPatch;
    }

    private String showingSuggestion;
    private void showNextSupportedSuggestion() {
        if (showingSuggestion != null) {
            return;
        }
        for (String suggestion : getMessagesController().pendingSuggestions) {
            if (showSuggestion(suggestion)) {
                showingSuggestion = suggestion;
                return;
            }
        }
    }

    private void onSuggestionDismiss() {
        if (showingSuggestion == null) {
            return;
        }
        getMessagesController().removeSuggestion(0, showingSuggestion);
        showingSuggestion = null;
        showNextSupportedSuggestion();
    }

    private boolean showSuggestion(String suggestion) {
        if ("AUTOARCHIVE_POPULAR".equals(suggestion)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("HideNewChatsAlertTitle", R.string.HideNewChatsAlertTitle));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("HideNewChatsAlertText", R.string.HideNewChatsAlertText)));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.setPositiveButton(LocaleController.getString("GoToSettings", R.string.GoToSettings), (dialog, which) -> {
                presentFragment(new PrivacySettingsActivity());
                AndroidUtilities.scrollToFragmentRow(parentLayout, "newChatsRow");
            });
            showDialog(builder.create(), dialog -> onSuggestionDismiss());
            return true;
        }
        return false;
    }

    private void updateChatsIndices() {
        if (viewPage == null) {
            return;
        }
        if (viewPage.getVisibility() == View.VISIBLE) {
            List<TLRPC.Chat> chats = chatsAdapter.getChatsArray();
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof SavedChannelCell) {
                    SavedChannelCell chatCell = (SavedChannelCell) child;
                    int index = -1;
                    for (int i = 0; i < chats.size(); i++) {
                        if (chats.get(i).id == chatCell.getChatId()) {
                            index = i;
                            break;
                        }
                    }
                    if (index < 0) {
                        continue;
                    }
                    chatCell.setDialogIndex(index);
                }
            }
        }
    }

    private void updateDialogIndices() {
        List<TLRPC.Chat> chats = chatsAdapter.getChatsArray();
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof SavedChannelCell) {
                SavedChannelCell savedChannelCell = (SavedChannelCell) child;
                String userName = savedChannelCell.getUserName();
                int index = -1;
                for (int i = 0; i < chats.size(); i++) {
                    if (chats.get(i).username != null && chats.get(i).username.equals(userName)) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    continue;
                }
                savedChannelCell.setDialogIndex(index);
            }
        }
    }

    private void updateVisibleRows(int mask) {
        updateVisibleRows(mask, true);
    }
    private void updateVisibleRows(int mask, boolean animated) {
        if (listView != null) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof SavedChannelCell) {
                    SavedChannelCell cell = (SavedChannelCell) child;
                    if ((mask & MessagesController.UPDATE_MASK_REORDER) != 0) {
                        cell.onReorderStateChanged(actionBar.isActionModeShowed(), true);
                    }
                    if ((mask & MessagesController.UPDATE_MASK_CHECK) != 0) {
                        cell.setChecked(false, (mask & MessagesController.UPDATE_MASK_CHAT) != 0);
                    } else {
                        if ((mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) != 0) {
                            cell.checkCurrentDialogIndex();
                            if (AndroidUtilities.isTablet()) {
                                cell.setDialogSelected(cell.getChatId() == openedDialogId);
                            }
                        } else if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) != 0) {
                            if (AndroidUtilities.isTablet()) {
                                cell.setDialogSelected(cell.getChatId() == openedDialogId);
                            }
                        } else {
                            cell.update(mask, animated);
                        }
                        cell.setChecked(chatsAdapter.containsSelectedDialog(cell.getChatId()), false);
                    }
                }


                if (child instanceof UserCell) {
                    ((UserCell) child).update(mask);
                } else if (child instanceof ProfileSearchCell) {
                    ProfileSearchCell cell = (ProfileSearchCell) child;
                    cell.update(mask);
                    cell.setChecked(chatsAdapter.containsSelectedDialog(cell.getDialogId()), false);
                }
                if (child instanceof RecyclerListView) {
                    RecyclerListView innerListView = (RecyclerListView) child;
                    int count2 = innerListView.getChildCount();
                    for (int b = 0; b < count2; b++) {
                        View child2 = innerListView.getChildAt(b);
                        if (child2 instanceof HintDialogCell) {
                            ((HintDialogCell) child2).update(mask);
                        }
                    }
                }
            }
        }
    }

    public void setDelegate(SavedChannelsActivityDelegate SavedChannelsActivityDelegate) {
        delegate = SavedChannelsActivityDelegate;
    }

    public boolean isMainDialogList() {
        return delegate == null;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) child).update(0);
                    } else if (child instanceof SavedChannelCell) {
                        ((SavedChannelCell) child).update(0);
                    } else if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
            if (actionBar != null) {
                actionBar.setPopupBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), true);
                actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false, true);
                actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, true);
                actionBar.setPopupItemsSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector), true);
            }
        };

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(fragmentView, 0, null, actionBarDefaultPaint, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, cellDelegate, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, new Drawable[]{Theme.dialogs_holidayDrawable}, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        arrayList.add(new ThemeDescription(selectedDialogsCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItemIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_dialogButtonSelector));

        if (listView != null) {
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messagePaint[1], null, null, Theme.key_chats_message_threeLines));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messagePaint[0], null, null, Theme.key_chats_message));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_draft));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_archivePinBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_archiveBackground));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_onlineCircle));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SavedChannelCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SavedChannelCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));

            GraySectionCell.createThemeDescriptions(arrayList, listView);

            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HashtagSearchCell.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundSaved));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundArchivedHidden));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessage));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_attachMessage));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessageArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessageArchived_threeLines));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_messageArchived));

        if (viewPage != null) {
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefaultArchived));

            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView1"}, null, null, null, Theme.key_chats_nameMessage_threeLines));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView2"}, null, null, null, Theme.key_chats_message));

            if (SharedConfig.archiveHidden) {
                arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchivedHidden));
                arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchivedHidden));
            } else {
                arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchived));
                arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchived));
            }
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box2", Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box1", Theme.key_avatar_text));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Arrow", Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box1", Theme.key_chats_archiveIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_hidePsaDrawable}, "Line 1", Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_hidePsaDrawable}, "Line 2", Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_hidePsaDrawable}, "Line 3", Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_hidePsaDrawable}, "Cup Red", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_hidePsaDrawable}, "Box", Theme.key_chats_archiveIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow1", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow2", Theme.key_chats_archivePinBackground));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{SavedChannelCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box1", Theme.key_chats_archiveIcon));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));

            arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

            arrayList.add(new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_archivePullDownBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_archivePullDownBackgroundActive));

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText));

        for (UndoView view : undoView) {
            arrayList.add(new ThemeDescription(view, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"subinfoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info1", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info2", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc12", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc11", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc10", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc9", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc8", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc7", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc6", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc5", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc4", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc3", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc2", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc1", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(view, 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Oval", Theme.key_undo_infoColor));
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackgroundGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextLink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLinkSelection));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_color_red));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_color_red));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray3));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_color_red));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextHint));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogInputField));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogInputFieldActivated));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareCheck));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareUnchecked));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareDisabled));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRadioBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRadioBackgroundChecked));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogButtonSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogScrollGlow));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRoundCheckBox));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRoundCheckBoxCheck));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLineProgress));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLineProgressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogGrayLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_inlineProgressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_inlineProgress));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchHint));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogFloatingButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogFloatingIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogShadowLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_sheet_scrollUp));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_sheet_other));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarTitle));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarSubtitle));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarItems));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_background));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_time));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progressCachedBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progress));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_button));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_buttonActive));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_player_time));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chat_messagePanelCursor));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_actionBarIconBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_groupcreate_spanBackground));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayGreen1));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayGreen2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayBlue1));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_topPanelGreen1));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_topPanelGreen2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_topPanelBlue1));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_topPanelBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_topPanelGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertGradientMuted));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertGradientMuted2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertGradientUnmuted));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertGradientUnmuted2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_mutedByAdminGradient));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_mutedByAdminGradient2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_mutedByAdminGradient3));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertMutedByAdmin));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_voipgroup_overlayAlertMutedByAdmin2));

        return arrayList;
    }

    float slideFragmentProgress = 1f;
    boolean isSlideBackTransition;
    boolean isDrawerTransition;
    ValueAnimator slideBackTransitionAnimator;

    @Override
    protected Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        if (backAnimation) {
            slideBackTransitionAnimator = ValueAnimator.ofFloat(slideFragmentProgress, 1f);
            return slideBackTransitionAnimator;
        }
        int duration = (int) (Math.max((int) (200.0f / getLayoutContainer().getMeasuredWidth() * distanceToMove), 80) * 1.2f);
        slideBackTransitionAnimator = ValueAnimator.ofFloat(slideFragmentProgress, 1f);
        slideBackTransitionAnimator.addUpdateListener(valueAnimator -> setSlideTransitionProgress((float) valueAnimator.getAnimatedValue()));
        slideBackTransitionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        slideBackTransitionAnimator.setDuration(duration);
        slideBackTransitionAnimator.start();
        return slideBackTransitionAnimator;
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        if (!topFragment && beginSlide) {
            isSlideBackTransition = true;
            setFragmentIsSliding(true);
        } else {
            slideBackTransitionAnimator = null;
            isSlideBackTransition = false;
            setFragmentIsSliding(false);
            setSlideTransitionProgress(1f);
        }
    }

    private void setFragmentIsSliding(boolean sliding) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        if (sliding) {
            if (viewPage != null) {
                viewPage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                viewPage.setClipChildren(false);
                viewPage.setClipToPadding(false);
                listView.setClipChildren(false);
            }

            if (actionBar != null) {
                actionBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            if (fragmentView != null) {
                ((ViewGroup) fragmentView).setClipChildren(false);
                fragmentView.requestLayout();
            }
        } else {
            if (viewPage != null) {
                viewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                viewPage.setClipChildren(true);
                viewPage.setClipToPadding(true);
                listView.setClipChildren(true);
            }
            if (actionBar != null) {
                actionBar.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            if (fragmentView != null) {
                ((ViewGroup) fragmentView).setClipChildren(true);
                fragmentView.requestLayout();
            }
        }
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        if (isSlideBackTransition && slideBackTransitionAnimator == null) {
            setSlideTransitionProgress(progress);
        }
    }

    private void setSlideTransitionProgress(float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        slideFragmentProgress = progress;
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    @Override
    public void setProgressToDrawerOpened(float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || isSlideBackTransition) {
            return;
        }
        boolean drawerTransition = progress > 0;
        if (drawerTransition != isDrawerTransition) {
            isDrawerTransition = drawerTransition;
            setFragmentIsSliding(isDrawerTransition);
            if (fragmentView != null) {
                fragmentView.requestLayout();
            }
        }
        setSlideTransitionProgress(1f - progress);
    }
}

