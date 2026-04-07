/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilesMigrationService;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SavedChannelsAdapter;
import org.telegram.ui.Cells.AnimatedStatusView;
import org.telegram.ui.Cells.SavedChannelCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.DialogsHintCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.DialogsActivityStatusLayout;
import org.telegram.ui.Components.DialogsActivityTopBubblesFadeView;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsItemAnimator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugProvider;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class SavedChannelsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, FloatingDebugProvider, FactorAnimator.Target, MainTabsActivity.TabFragmentDelegate {
    public static final int MAIN_TABS_HEIGHT = 56;
    public static final int MAIN_TABS_MARGIN = 8;
    public static final int MAIN_TABS_HEIGHT_WITH_MARGINS = MAIN_TABS_HEIGHT + MAIN_TABS_MARGIN * 2;
    public static final int FILTER_TABS_HEIGHT = 36;
    public static final int SEARCH_TABS_HEIGHT = 36 + 7 + 7;

    private final int ANIMATOR_ID_SHADOW_VISIBLE = 4;
    private final int ANIMATOR_ID_ACTION_MODE_VISIBLE = 6;
    private final int ANIMATOR_ID_FORWARD_BUTTON_VISIBLE = 7;

    private final BoolAnimator animatorShadowVisible = new BoolAnimator(ANIMATOR_ID_SHADOW_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    private final BoolAnimator animatorActionModeVisible = new BoolAnimator(ANIMATOR_ID_ACTION_MODE_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
    private final BoolAnimator animatorForwardButtonVisible = new BoolAnimator(ANIMATOR_ID_FORWARD_BUTTON_VISIBLE,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);


    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);


    private TLRPC.RequestPeerType requestPeerType;
    private long requestPeerBotId;
    private float scrollYOffset;
    private boolean invalidateScrollY = true;
    public boolean hasMainTabs;

    public MessagesStorage.TopicKey getOpenedDialogId() {
        return openedDialogId;
    }

    public class ViewPage extends FrameLayout {
        public int pageAdditionalOffset;
        public DialogsRecyclerView listView;
        public RecyclerListViewScroller scroller;
        private LinearLayoutManager layoutManager;
        private SavedChannelsAdapter dialogsAdapter;
        private ItemTouchHelper itemTouchhelper;
        private SwipeController swipeController;
        private RecyclerAnimationScrollHelper scrollHelper;
        private FlickerLoadingView progressView;
        private DialogsItemAnimator dialogsItemAnimator;

        private RecyclerListView animationSupportListView;
        private SavedChannelsAdapter animationSupportSavedChannelsAdapter;

        public ViewPage(Context context) {
            super(context);
        }

        public boolean isDefaultDialogType() {
            return true;
        }

        boolean updating;

        Runnable saveScrollPositionRunnable = () -> {
            if (listView != null && listView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && listView.getChildCount() > 0 && listView.getLayoutManager() != null) {
                LinearLayoutManager layoutManager = ((LinearLayoutManager) listView.getLayoutManager());
                View view = null;
                int position = -1;
                int top = Integer.MAX_VALUE;
                for (int i = 0; i < listView.getChildCount(); i++) {
                    int childPosition = listView.getChildAdapterPosition(listView.getChildAt(i));
                    View child = listView.getChildAt(i);
                    if (childPosition != RecyclerListView.NO_POSITION && child != null && child.getTop() < top) {
                        view = child;
                        position = childPosition;
                        top = child.getTop();
                    }
                }
                if (view != null) {
                    float offset = view.getTop() - listView.getPaddingTop();
                    if (listView.getScrollState() != RecyclerView.SCROLL_STATE_DRAGGING) {
                        layoutManager.scrollToPositionWithOffset(position, (int) offset);
                    }
                }
            }
        };

        Runnable updateListRunnable = () -> {
            dialogsAdapter.updateList(saveScrollPositionRunnable);
            invalidateScrollY = true;
            listView.updateDialogsOnNextDraw = true;
            updating = false;
            listView.invalidate();
        };

        @Override
        public void setTranslationY(float translationY) {
            if (getTranslationY() != translationY) {
                blur3_InvalidateBlur();
            }
            super.setTranslationY(translationY);
        }

        @Override
        public void setTranslationX(float translationX) {
            if (getTranslationX() != translationX) {
                super.setTranslationX(translationX);
                blur3_InvalidateBlur();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            FrameLayout.LayoutParams lp = (LayoutParams) listView.getLayoutParams();
            lp.bottomMargin = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void updateList(boolean animated) {
            if (isPaused) {
                return;
            }
            if (animated) {
                AndroidUtilities.cancelRunOnUIThread(updateListRunnable);
                listView.setItemAnimator(dialogsItemAnimator);
                updateListRunnable.run();
                return;
            }
            if (updating) {
                return;
            }
            updating = true;
            if (!dialogsItemAnimator.isRunning()) {
                listView.setItemAnimator(null);
            }
            AndroidUtilities.runOnUIThread(updateListRunnable, 36);
        }
    }

    private float contactsAlpha = 1f;
    private ValueAnimator contactsAlphaAnimator;
    private ViewPage viewPage;
    public static boolean switchingTheme;
    private int undoViewIndex;
    private UndoView[] undoView = new UndoView[2];
    @Nullable
    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private DialogsActivityStatusLayout dialogsActivityStatusLayout;
    float searchViewPagerTranslationY;
    float panTranslationY;

    private View blurredView;

    private ItemOptions filterOptions;

    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;

    private SavedChannelCell movingView;
    private SavedChannelsAdapter savedChannelsMainAdapter;
    private boolean allowMoving;
    private boolean waitingForScrollFinished;
    private boolean updatePullAfterScroll;

    private final Paint actionBarDefaultPaint = new Paint();


    private FragmentContextView fragmentLocationContextView;
    private FrameLayout fragmentLocationContextViewWrapper;
    private FragmentContextView fragmentContextView;
    private FrameLayout fragmentContextViewWrapper;
    private DialogsActivityTopPanelLayout topPanelLayout;
    private DialogsActivityTopBubblesFadeView topBubblesFadeView;

    private ArrayList<TLRPC.Chat> frozenDialogsList;
    private boolean dialogsListFrozen;

    private int currentConnectionState;

    private boolean disableActionBarScrolling;

    private MessagesStorage.TopicKey openedDialogId = new MessagesStorage.TopicKey();

    private ArrayList<Long> selectedDialogs = new ArrayList<>();
    public boolean notify = true;
    public int scheduleDate;
    public int scheduleRepeatPeriod;

    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    public boolean whiteActionBar;
    private float progressToActionMode;
    private ValueAnimator actionBarColorAnimator;

    //
    private float scrollAdditionalOffset;

    private int debugLastUpdateAction = -1;

    private AnimatedStatusView animatedStatusView;

    public final Property<SavedChannelsActivity, Float> SCROLL_Y = new AnimationProperties.FloatProperty<SavedChannelsActivity>("animationValue") {
        @Override
        public void setValue(SavedChannelsActivity object, float value) {
            object.setScrollY(value);
        }

        @Override
        public Float get(SavedChannelsActivity object) {
            return scrollYOffset;
        }
    };

    public final Property<View, Float> SEARCH_TRANSLATION_Y = new AnimationProperties.FloatProperty<View>("viewPagerTranslation") {
        @Override
        public void setValue(View object, float value) {
            searchViewPagerTranslationY = value;
            object.setTranslationY(panTranslationY + searchViewPagerTranslationY);
        }

        @Override
        public Float get(View object) {
            return searchViewPagerTranslationY;
        }
    };

    private class ContentView extends SizeNotifierFrameLayout {
        public ContentView(Context context) {
            super(context);
        }

        @Override
        public void invalidateBlur() {
            super.invalidateBlur();
            blur3_InvalidateBlur();
        }

        public int getActionBarFullHeight() {
            float h = actionBar.getHeight();
            return (int) h;
        }

        public int getActionBarTop() {
            float scrollY = scrollYOffset;
            scrollY *= (1f - progressToActionMode);
            return (int) (-getY() + scrollY);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == blurredView) {
                return true;
            }
            if (SizeNotifierFrameLayout.drawingBlur) {
                return super.drawChild(canvas, child, drawingTime);
            }
            boolean result;
            if (child == viewPage || child == topPanelLayout) {
                canvas.save();

                final boolean doNotClip = child == topPanelLayout;
                if (!doNotClip) {
                    canvas.clipRect(0, -getY() + getActionBarTop() + getActionBarFullHeight(), getMeasuredWidth(), getMeasuredHeight());
                }
                if (slideFragmentProgress != 1f) {
                    if (slideFragmentLite) {
                        canvas.translate((-1) * dp(slideAmplitudeDp) * (1f - slideFragmentProgress), 0);
                    } else {
                        final float s = 1f - 0.05f * (1f - slideFragmentProgress);
                        canvas.translate((-dp(4)) * (1f - slideFragmentProgress), 0);
                        canvas.scale(s, s, 0, -getY() + scrollYOffset + getActionBarFullHeight());
                    }
                }
                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            } else if (child == actionBar && slideFragmentProgress != 1f) {
                canvas.save();
                if (slideFragmentLite) {
                    canvas.translate((-1) * dp(slideAmplitudeDp) * (1f - slideFragmentProgress), 0);
                } else {
                    float s = 1f - 0.05f * (1f - slideFragmentProgress);
                    canvas.translate((-dp(4)) * (1f - slideFragmentProgress), 0);
                    canvas.scale(s, s, 0, (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2f);
                }
                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            } else {
                result = super.drawChild(canvas, child, drawingTime);
            }
            return result;
        }

        @Override
        public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !SharedConfig.chatBlurEnabled() || iBlur3SourceGlassFrosted == null || !BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
                canvas.drawRect(rectTmp, blurScrimPaint);
                return;
            }

            final boolean isThemeLight = resourceProvider != null ? !resourceProvider.isDark() : !Theme.isCurrentThemeDark();
            int blurAlpha = isThemeLight ? 216 : ChatActivity.ACTION_BAR_BLUR_ALPHA;
            canvas.save();
            canvas.translate(0, -y);
            iBlur3SourceGlassFrosted.draw(canvas, rectTmp.left, rectTmp.top + y, rectTmp.right, rectTmp.bottom + y);
            canvas.restore();

            final int oldScrimAlpha = blurScrimPaint.getAlpha();
            blurScrimPaint.setAlpha(blurAlpha);
            canvas.drawRect(rectTmp, blurScrimPaint);
            blurScrimPaint.setAlpha(oldScrimAlpha);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (Build.VERSION.SDK_INT >= 31 && scrollableViewNoiseSuppressor != null) {
                blur3_InvalidateBlur();
            }

            if (invalidateScrollY && progressToActionMode == 0) {
                invalidateScrollY = false;
                int firstItemPosition = 0;
                DialogsRecyclerView recyclerView = viewPage.listView;

                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForLayoutPosition(firstItemPosition);
                if (holder != null) {
                    float visiblePartAfterScroll = recyclerView.getPaddingTop() - holder.itemView.getY();
                    if (visiblePartAfterScroll >= 0) {
                        int maxScrollYOffset = 0;
                        float newTranslation = -visiblePartAfterScroll;
                        if (newTranslation < -maxScrollYOffset) {
                            newTranslation = -maxScrollYOffset;
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        SavedChannelsActivity.this.setScrollY(newTranslation);
                    } else {
                        SavedChannelsActivity.this.setScrollY(0);
                    }
                } else {
                    SavedChannelsActivity.this.setScrollY(0);
                }
            }
            final int actionBarHeight = getActionBarFullHeight();
            final int top;
            if (inPreviewMode) {
                top = AndroidUtilities.statusBarHeight;
            } else {
                top = getActionBarTop();
            }
            updateContextViewPosition();
            super.dispatchDraw(canvas);
            drawHeaderShadow(canvas, top + actionBarHeight);
            
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
            if (!hasMainTabs) {
                AndroidUtilities.drawNavigationBarProtection(canvas, this, getThemedColor(Theme.key_windowBackgroundWhite), navigationBarHeight);
            }
        }

        @Override
        protected boolean invalidateOptimized() {
            return true;
        }

        private boolean wasPortrait;

        @Override
        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
            final int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);
            boolean portrait = heightSize > widthSize;

            setMeasuredDimension(widthSize, heightSize);

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
                    int h = View.MeasureSpec.getSize(heightMeasureSpec);
                    int contentHeightSpec = View.MeasureSpec.makeMeasureSpec(Math.max(dp(10), h + dp(2) - actionBar.getMeasuredHeight()), View.MeasureSpec.EXACTLY);
                    child.measure(contentWidthSpec, contentHeightSpec);
                } else if (child instanceof ViewPage) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int h = heightSize + dp(2);
                    if (actionBarColorAnimator == null) {
                        child.setTranslationY(0);
                    }
                    int transitionPadding = ((isSlideBackTransition) ? (int) (h * 0.05f) : 0);
                    h += transitionPadding;
                    child.setPadding(child.getPaddingLeft(), child.getPaddingTop(), child.getPaddingRight(), transitionPadding);
                    child.measure(contentWidthSpec, View.MeasureSpec.makeMeasureSpec(Math.max(dp(10), h), View.MeasureSpec.EXACTLY));
                    child.setPivotX(child.getMeasuredWidth() / 2f);
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            if (portrait != wasPortrait) {
                post(() -> {
                    if (selectAnimatedEmojiDialog != null) {
                        selectAnimatedEmojiDialog.dismiss();
                        selectAnimatedEmojiDialog = null;
                    }
                });
                wasPortrait = portrait;
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom = 0;
            int keyboardSize = measureKeyboardHeight();
            setBottomClip(paddingBottom);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE) {
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
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }

                if (child instanceof DatabaseMigrationHint) {
                    childTop = actionBar.getMeasuredHeight();
                } else if (child instanceof ViewPage) {
                    childTop = 0;
                } else if (child == topPanelLayout || child == topBubblesFadeView) {
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

    public class DialogsRecyclerView extends BlurredRecyclerView {

        public boolean updateDialogsOnNextDraw;
        private boolean firstLayout = true;
        private boolean ignoreLayout;
        private final ViewPage parentPage;
        private int lastListPadding;

        Paint paint = new Paint();
        RectF rectF = new RectF();
        private RecyclerListView animationSupportListView;
        LongSparseArray<View> animationSupportViewsByDialogId;
        private Paint selectorPaint;
        float lastDrawSelectorY;
        float selectorPositionProgress = 1f;
        float animateFromSelectorPosition;
        boolean animateSwitchingSelector;
        public int additionalPadding;

        public DialogsRecyclerView(Context context, ViewPage page) {
            super(context);
            parentPage = page;
            additionalClipBottom = dp(200);
        }

        public void prepareSelectorForAnimation() {
            selectorPositionProgress = 0;
            animateFromSelectorPosition = lastDrawSelectorY;
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
        protected int measureBlurTopPadding() {
            return dp(48);
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
            canvas.save();

            int maxSupportedViewsPosition = Integer.MIN_VALUE;
            int minSupportedViewsPosition = Integer.MAX_VALUE;

            if (animationSupportListView != null) {
                if (animationSupportViewsByDialogId == null) {
                    animationSupportViewsByDialogId = new LongSparseArray<>();
                }

                for (int i = 0; i < animationSupportListView.getChildCount(); i++) {
                    View child = animationSupportListView.getChildAt(i);
                    if (child instanceof SavedChannelCell && child.getBottom() > 0) {
                        animationSupportViewsByDialogId.put(((SavedChannelCell) child).getDialogId(), child);
                    }
                }
            }

            float maxTop = Integer.MAX_VALUE;
            float maxBottom = Integer.MIN_VALUE;
            SavedChannelCell selectedCell = null;

            float scrollOffset = scrollYOffset;

            for (int i = 0; i < getChildCount(); i++) {
                View view = getChildAt(i);
                SavedChannelCell dialogCell = null;
                if (view instanceof SavedChannelCell) {
                    dialogCell = (SavedChannelCell) view;
                    if (AndroidUtilities.isTablet()) {
                        dialogCell.setDialogSelected(dialogCell.getDialogId() == openedDialogId.dialogId);
                    }
                    if (animationSupportViewsByDialogId != null && animationSupportListView != null) {
                        View animateToView = animationSupportViewsByDialogId.get(dialogCell.getDialogId());

                        animationSupportViewsByDialogId.delete(dialogCell.getDialogId());
                        if (animateToView != null) {
                            int supportViewPosition = animationSupportListView.getChildLayoutPosition(animateToView);
                            if (supportViewPosition > maxSupportedViewsPosition) {
                                maxSupportedViewsPosition = supportViewPosition;
                            }
                            if (supportViewPosition < minSupportedViewsPosition) {
                                minSupportedViewsPosition = supportViewPosition;
                            }
                        }
                    }
                    if (updateDialogsOnNextDraw) {
                        if (dialogCell.update(0, true)) {
                            int p = getChildAdapterPosition(dialogCell);
                            if (p >= 0) {
                                getAdapter().notifyItemChanged(p);
                            }
                        }
                    }
                }
                if (animationSupportListView != null) {
                    int restoreCount = canvas.save();

                    canvas.translate(view.getX(), view.getY());
                    if (dialogCell != null) {
                        dialogCell.rightFragmentOffset = -scrollOffset;
                    } else {
                        canvas.saveLayerAlpha(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
                    }
                    view.draw(canvas);

                    if (dialogCell != null && dialogCell != selectedCell) {
                        dialogCell.collapseOffset = 0;
                        dialogCell.rightFragmentOffset = 0;
                    }
                    canvas.restoreToCount(restoreCount);
                }
            }


            if (selectedCell != null) {
                canvas.save();
                lastDrawSelectorY = selectedCell.getY() + selectedCell.collapseOffset + selectedCell.avatarImage.getImageY();
                selectedCell.collapseOffset = 0;
                selectedCell.rightFragmentOffset = 0;
                if (selectorPositionProgress != 1f) {
                    selectorPositionProgress += 16 / 200f;
                    selectorPositionProgress = Utilities.clamp(selectorPositionProgress, 1f, 0f);
                    invalidate();
                }
                float selectorPositionProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(this.selectorPositionProgress);
                boolean animateInOut = false;
                if (selectorPositionProgress != 1f && animateFromSelectorPosition != Integer.MIN_VALUE) {
                    if (Math.abs(animateFromSelectorPosition - lastDrawSelectorY) < getMeasuredHeight() * 0.4f) {
                        lastDrawSelectorY = AndroidUtilities.lerp(animateFromSelectorPosition, lastDrawSelectorY, selectorPositionProgress);
                    } else {
                        animateInOut = true;
                    }
                }

                float hideProgrss = animateSwitchingSelector && (animateInOut || animateFromSelectorPosition == Integer.MIN_VALUE) ? (1f - selectorPositionProgress) : 1f;
                if (hideProgrss == 1f) {
                    lastDrawSelectorY = Integer.MIN_VALUE;
                }
                float xOffset = -dp(5) * hideProgrss;
                AndroidUtilities.rectTmp.set(-dp(4) + xOffset, lastDrawSelectorY - dp(1), dp(4) + xOffset, lastDrawSelectorY + selectedCell.avatarImage.getImageHeight() + dp(1));
                if (selectorPaint == null) {
                    selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                selectorPaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), selectorPaint);
                canvas.restore();
            } else {
                lastDrawSelectorY = Integer.MIN_VALUE;
            }

            //undrawing views
            if (animationSupportViewsByDialogId != null) {
                float maxUndrawTop = Integer.MIN_VALUE;
                float maxUndrawBottom = Integer.MAX_VALUE;
                for (int i = 0; i < animationSupportViewsByDialogId.size(); i++) {
                    View view = animationSupportViewsByDialogId.valueAt(i);
                    int position = animationSupportListView.getChildLayoutPosition(view);
                    if (position < minSupportedViewsPosition && view.getTop() > maxUndrawTop) {
                        maxUndrawTop = view.getTop();
                    }
                    if (position > maxSupportedViewsPosition && view.getBottom() < maxUndrawBottom) {
                        maxUndrawBottom = view.getBottom();
                    }
                }
                for (int i = 0; i < animationSupportViewsByDialogId.size(); i++) {
                    View view = animationSupportViewsByDialogId.valueAt(i);
                    if (view instanceof SavedChannelCell) {
                        int position = animationSupportListView.getChildLayoutPosition(view);
                        SavedChannelCell dialogCell = (SavedChannelCell) view;
                        dialogCell.isTransitionSupport = false;
                        dialogCell.buildLayout();
                        dialogCell.isTransitionSupport = true;

                        int restoreCount = canvas.save();
                        if (position > maxSupportedViewsPosition) {
                            canvas.translate(view.getX(), maxBottom + view.getBottom() - maxUndrawBottom);
                        } else {
                            canvas.translate(view.getX(), maxBottom + view.getTop() - maxUndrawTop);
                        }
                        view.draw(canvas);

                        canvas.restoreToCount(restoreCount);
                    }
                }
                animationSupportViewsByDialogId.clear();
            }

            updateDialogsOnNextDraw = false;
            if (animationSupportListView != null) {
                invalidate();
            }

            if (animationSupportListView == null) {
                super.dispatchDraw(canvas);
            }

            if (drawMovingViewsOverlayed()) {
                paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                for (int i = 0; i < getChildCount(); i++) {
                    View view = getChildAt(i);

                    if (view instanceof SavedChannelsAdapter.LastEmptyView && ((SavedChannelsAdapter.LastEmptyView) view).moving) {
                        if (view.getAlpha() != 1f) {
                            rectF.set(view.getX(), view.getY(), view.getX() + view.getMeasuredWidth(), view.getY() + view.getMeasuredHeight());
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
            return getItemAnimator() != null && getItemAnimator().isRunning();
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

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int t = 0;
            int pos = parentPage.layoutManager.findFirstVisibleItemPosition();
            if (pos != RecyclerView.NO_POSITION && parentPage.itemTouchhelper.isIdle() && !parentPage.layoutManager.hasPendingScrollPosition() && parentPage.listView.getScrollState() != RecyclerView.SCROLL_STATE_DRAGGING) {
                RecyclerView.ViewHolder holder = parentPage.listView.findViewHolderForAdapterPosition(pos);
                if (holder != null) {
                    int top = holder.itemView.getTop();
                    ignoreLayout = true;
                    parentPage.layoutManager.scrollToPositionWithOffset(pos, (int) (top - lastListPadding + scrollAdditionalOffset + parentPage.pageAdditionalOffset));
                    ignoreLayout = false;
                }
            } else if (pos == RecyclerView.NO_POSITION && firstLayout) {
                parentPage.layoutManager.scrollToPositionWithOffset(0, (int) scrollYOffset);
            }

            ignoreLayout = true;
            t = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            additionalPadding = 0;

            final float filterTabsVisibility = 0f;
            final float topPanelsVisibility = topPanelLayout != null ? topPanelLayout.getMetadata().getTotalVisibility() : 0f;

            t += (int) (dp(36 + 14) * filterTabsVisibility);
            additionalPadding += (int) (dp(36 + 14) * filterTabsVisibility);

            if (topPanelLayout != null) {
                final int h = (int) topPanelLayout.getAnimatedHeightWithPadding(lerp((float) dp(14), dp(7), filterTabsVisibility));
                t += h;
                additionalPadding += h;
            }

            t -= dp(5 * Math.max(filterTabsVisibility, topPanelsVisibility));
            additionalPadding -= dp(5 * Math.max(filterTabsVisibility, topPanelsVisibility));

            final int b = calculateListViewPaddingBottom();
            if (t != topPadding || b != getPaddingBottom()) {
                setTopGlowOffset(t);
                setPadding(0, t, 0, b);
                parentPage.progressView.setPaddingTop(t);
                for (int i = 0; i < getChildCount(); i++) {
                    if (getChildAt(i) instanceof SavedChannelsAdapter.LastEmptyView) {
                        getChildAt(i).requestLayout();
                    }
                }
            }
            ignoreLayout = false;

            if (firstLayout && getMessagesController().dialogsLoaded) {
                firstLayout = false;
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            lastListPadding = getPaddingTop();
            scrollAdditionalOffset = 0;
            parentPage.pageAdditionalOffset = 0;
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
            if (fastScrollAnimationRunning || waitingForScrollFinished) {
                return false;
            }
            int action = e.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            }
            boolean result = super.onTouchEvent(e);
            return result;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (fastScrollAnimationRunning || waitingForScrollFinished || parentPage.dialogsItemAnimator.isRunning()) {
                return false;
            }
            return super.onInterceptTouchEvent(e);
        }

        @Override
        protected boolean allowSelectChildAtPosition(View child) {
            if (child instanceof HeaderCell && !child.isClickable()) {
                return false;
            }
            return true;
        }

        public void setAnimationSupportView(RecyclerListView animationSupportListView, float scrollOffset, boolean opened, boolean backward) {
            RecyclerListView anchorListView = animationSupportListView == null ? this.animationSupportListView : this;
            SavedChannelCell anchorView = null;
            SavedChannelCell selectedDialogView = null;
            if (anchorListView == null) {
                this.animationSupportListView = animationSupportListView;
                return;
            }
            int maxTop = Integer.MAX_VALUE;
            int padding = 0;//getPaddingTop();
//            if (hasStories) {
//                padding -= AndroidUtilities.dp(DialogStoriesCell.HEIGHT_IN_DP);
//            }
            for (int i = 0; i < anchorListView.getChildCount(); i++) {
                View child = anchorListView.getChildAt(i);
                if (child instanceof SavedChannelCell) {
                    SavedChannelCell dialogCell = (SavedChannelCell) child;
                    if (child.getTop() >= padding && dialogCell.getDialogId() != 0 && child.getTop() < maxTop) {
                        anchorView = (SavedChannelCell) child;
                        maxTop = anchorView.getTop();
                    }
                }
            }
            if (selectedDialogView != null && getAdapter().getItemCount() * dp(70) > getMeasuredHeight() && (anchorView.getTop() - getPaddingTop()) > (getMeasuredHeight() - getPaddingTop()) / 2f) {
                anchorView = selectedDialogView;
            }
            this.animationSupportListView = animationSupportListView;

            if (anchorView != null) {
                if (animationSupportListView != null) {
                    int topPadding = this.topPadding;
                    animationSupportListView.setPadding(getPaddingLeft(), topPadding, getPaddingLeft(), getPaddingBottom());
                    if (anchorView != null) {
                        SavedChannelsAdapter adapter = (SavedChannelsAdapter) animationSupportListView.getAdapter();
                        int p = adapter.findDialogPosition(anchorView.getDialogId());
                        int offset = (int) (anchorView.getTop() - anchorListView.getPaddingTop() + scrollOffset);
                        if (p >= 0) {
                            int fixedOffset = adapter.fixScrollGap(this, p, offset);
                            ((LinearLayoutManager) animationSupportListView.getLayoutManager()).scrollToPositionWithOffset(p, fixedOffset);
                        }
                    }
                }
               // if (!backward) {
                    SavedChannelsAdapter adapter = (SavedChannelsAdapter) getAdapter();
                    int p = adapter.findDialogPosition(anchorView.getDialogId());
                    int offset = (int) (anchorView.getTop() - getPaddingTop());
                    if (p >= 0) {
                        ((LinearLayoutManager) getLayoutManager()).scrollToPositionWithOffset(p, offset);
                    }
               // }
            }
        }
    }

    private class SwipeController extends ItemTouchHelper.Callback {

        private RecyclerView.ViewHolder currentItemViewHolder;
        private ViewPage parentPage;

        public SwipeController(ViewPage page) {
            parentPage = page;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            if (waitingForDialogsAnimationEnd(parentPage) || parentLayout != null && parentLayout.isInPreviewMode()) {
                return 0;
            }
            if (parentPage.isDefaultDialogType() && viewHolder.itemView instanceof SavedChannelCell) {
                SavedChannelCell dialogCell = (SavedChannelCell) viewHolder.itemView;
                long dialogId = dialogCell.getDialogId();
                if (actionBar.isActionModeShowed(null)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    if (!allowMoving || chat == null || !isChatPinned(chat)) {
                        return 0;
                    }
                    movingView = (SavedChannelCell) viewHolder.itemView;
                    movingView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                } else if (actionBar.isActionModeShowed(SAVED_CHANNEL_ACTION_MODE_TAG)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    if (!allowMoving || chat == null || !isChatPinned(chat)) {
                        return 0;
                    }
                    movingView = (SavedChannelCell) viewHolder.itemView;
                    movingView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                } else {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT);
                }
            }
            return 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (!(target.itemView instanceof SavedChannelCell)) {
                return false;
            }
            SavedChannelCell dialogCell = (SavedChannelCell) target.itemView;
            long dialogId = dialogCell.getDialogId();
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat == null || !isChatPinned(chat)) {
                return false;
            }
            int fromIndex = source.getAdapterPosition();
            int toIndex = target.getAdapterPosition();
            if (parentPage.listView.getItemAnimator() == null) {
                parentPage.listView.setItemAnimator(parentPage.dialogsItemAnimator);
            }

            parentPage.dialogsAdapter.moveSavedChannelDialogs(fromIndex, toIndex);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder != null) {
                parentPage.listView.hideSelector(false);
            }
            currentItemViewHolder = viewHolder;
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public long getAnimationDuration(@NonNull RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
                return 200;
            } else if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
                if (movingView != null) {
                    View view = movingView;
                    AndroidUtilities.runOnUIThread(() -> view.setBackgroundDrawable(null), parentPage.dialogsItemAnimator.getMoveDuration());
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

        iBlur3SourceColor = new BlurredBackgroundSourceColor();
        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            iBlur3SourceGlassFrosted = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceGlassFrosted.setupRenderer(new RenderNodeWithHash.Renderer() {
                @Override
                public void renderNodeCalculateHash(IBlur3Hash hash) {
                    hash.add(getThemedColor(Theme.key_windowBackgroundWhite));
                    hash.add(SharedConfig.chatBlurEnabled());

                }

                @Override
                public void renderNodeUpdateDisplayList(Canvas canvas) {
                    final int width = fragmentView.getMeasuredWidth();
                    final int height = fragmentView.getMeasuredHeight();

                    canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    if (SharedConfig.chatBlurEnabled()) {
                        scrollableViewNoiseSuppressor.draw(canvas, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
                    }
                }
            });

            iBlur3SourceGlass = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceGlass.setupRenderer(new RenderNodeWithHash.Renderer() {
                @Override
                public void renderNodeCalculateHash(IBlur3Hash hash) {
                    hash.add(getThemedColor(Theme.key_windowBackgroundWhite));
                    hash.add(SharedConfig.chatBlurEnabled());

                }

                @Override
                public void renderNodeUpdateDisplayList(Canvas canvas) {
                    final int width = fragmentView.getMeasuredWidth();
                    final int height = fragmentView.getMeasuredHeight();

                    canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    if (SharedConfig.chatBlurEnabled()) {
                        scrollableViewNoiseSuppressor.draw(canvas, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                    }
                }
            });

            iBlur3FactoryFrostedLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlassFrosted);
            iBlur3FactoryFrostedLiquidGlass.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
            iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlass);
            iBlur3FactoryLiquidGlass.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
            iBlur3FactoryBlur = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlassFrosted);
        } else {
            scrollableViewNoiseSuppressor = null;
            iBlur3SourceGlassFrosted = null;
            iBlur3SourceGlass = null;
            iBlur3FactoryFrostedLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
            iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
            iBlur3FactoryBlur = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        }
        iBlur3FactoryFade = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
    }

    private MainTabsActivityController mainTabsActivityController;

    public void setMainTabsActivityController(MainTabsActivityController controller) {
        mainTabsActivityController = controller;
    }


    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        if (arguments != null) {
            hasMainTabs = arguments.getBoolean("hasMainTabs", false);

            byte[] requestPeerTypeBytes = arguments.getByteArray("requestPeerType");
            if (requestPeerTypeBytes != null) {
                try {
                    SerializedData buffer = new SerializedData(requestPeerTypeBytes);
                    requestPeerType = TLRPC.RequestPeerType.TLdeserialize(buffer, buffer.readInt32(true), true);
                    buffer.cleanup();
                } catch (Exception e) {
                }
            }
            requestPeerBotId = arguments.getLong("requestPeerBotId", 0);
        }

        SharedConfig.loadProxyList();
        currentConnectionState = getConnectionsManager().getConnectionState();

        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.openedChatChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().addObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().addObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.topicsDidLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.reloadHints);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().addObserver(this, NotificationCenter.needDeleteDialog);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsUnreadReactionsCounterChanged);

        getNotificationCenter().addObserver(this, NotificationCenter.dialogTranslate);
        getNotificationCenter().addObserver(this, NotificationCenter.savedChannelAdded);

        getMessagesController().getStoriesController().loadStories();

        getContactsController().loadGlobalPrivacySetting();

        if (getMessagesController().savedViewAsChats) {
            getMessagesController().getSavedMessagesController().preloadDialogs(true);
        }

        additionNavigationBarHeight = hasMainTabs ? dp(MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.openedChatChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().removeObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.topicsDidLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.reloadHints);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().removeObserver(this, NotificationCenter.needDeleteDialog);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsUnreadReactionsCounterChanged);

        getNotificationCenter().removeObserver(this, NotificationCenter.dialogTranslate);
        getNotificationCenter().removeObserver(this, NotificationCenter.savedChannelAdded);
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        notificationsLocker.unlock();
        SuggestClearDatabaseBottomSheet.dismissDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return !(dialog instanceof BotWebViewSheet) && super.dismissDialogOnPause(dialog);
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context, resourceProvider) {

            @Override
            public void setTranslationY(float translationY) {
                if (translationY != getTranslationY() && fragmentView != null) {
                    fragmentView.invalidate();
                }
                super.setTranslationY(translationY);
            }

            @Override
            protected boolean onSearchChangedIgnoreTitles() {
                return false;
            }

            @Override
            public void onSearchFieldVisibilityChanged(boolean visible) {
                super.onSearchFieldVisibilityChanged(visible);
            }

            @Override
            public void showActionMode(boolean animated, View extraView, View showingView, View[] hidingViews, boolean[] hideView, View translationView, int translation) {
                super.showActionMode(animated, extraView, showingView, hidingViews, hideView, translationView, translation);
                animatorActionModeVisible.setValue(true, animated);
            }

            @Override
            public void hideActionMode() {
                super.hideActionMode();
                animatorActionModeVisible.setValue(false, true);
            }
        };
        actionBar.setAllowOverlayTitle(true);
        actionBar.setUseContainerForTitles();
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);
        actionBar.createAdditionalSubTitleOverlayContainer();
        actionBar.getAdditionalSubTitleOverlayContainer().setTranslationX(dp(4));
        actionBar.getAdditionalSubTitleOverlayContainer().setTranslationY(-dp(3));
        return actionBar;
    }

    @Override
    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        super.setTitleOverlayText(title, titleId, action);
        if (actionBar != null && selectAnimatedEmojiDialog != null && selectAnimatedEmojiDialog.getContentView() instanceof SelectAnimatedEmojiDialog) {
            SimpleTextView textView = actionBar.getTitleTextView();
            ((SelectAnimatedEmojiDialog) selectAnimatedEmojiDialog.getContentView()).setScrimDrawable(null, textView);
        }
    }

    @Override
    public View createView(final Context context) {
        selectedDialogs.clear();

        AndroidUtilities.runOnUIThread(() -> Theme.createChatResources(context, false));

        topPanelLayout = null;
        if (!hasMainTabs) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setTitle(LocaleController.getString(R.string.SavedChannels));
        //if (!onlySelect || initialDialogsType == DIALOGS_TYPE_FORWARD) {
            actionBar.setAddToContainer(false);
            actionBar.setCastShadows(false);
            actionBar.setClipContent(true);
        //}
        actionBar.setTitleActionRunnable(() -> {
            scrollToTop(true);
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (!org.telegram.messenger.fakepasscode.FakePasscodeUtils.isFakePasscodeActivated()) {
            refreshItem = menu.addItem(refresh, R.drawable.menu_browser_refresh);
            refreshItem.setContentDescription(LocaleController.getString(R.string.Refresh));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode(true);
                    } else if (!hasMainTabs) {
                        finishFragment();
                    }
                } else if (id == pin) {
                    performSelectedDialogsAction(savedChannelsMainAdapter.getSelectedUserNames(), id);
                } else if (id == delete) {
                    performSelectedDialogsAction(savedChannelsMainAdapter.getSelectedUserNames(), id);
                } else if (id == refresh) {
                    doRefresh();
                }
            }
        });

        ContentView contentView = new ContentView(context);
        fragmentView = contentView;

        viewPositionWatcher = new ViewPositionWatcher(contentView);
        iBlur3FactoryFrostedLiquidGlass.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryLiquidGlass.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryFade.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryBlur.setSourceRootView(viewPositionWatcher, contentView);

        final PointF tmpPoint = new PointF();
        iBlur3Capture = (canvas, position) -> {
            if (viewPage != null && viewPage.getVisibility() == View.VISIBLE && viewPage.getAlpha() > 0f) {
                Blur3Utils.captureRelativeParent(viewPage.listView, canvas, position, viewPage.listView, contentView, 255);
            }
        };

        viewPage = new ViewPage(context);
        contentView.addView(viewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        {

            viewPage.progressView = new FlickerLoadingView(context);
            viewPage.progressView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
            viewPage.progressView.setVisibility(View.GONE);
            viewPage.addView(viewPage.progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            viewPage.listView = new DialogsRecyclerView(context, viewPage);
            viewPage.listView.addEdgeEffectListener(() -> viewPage.listView.postOnAnimation(this::blur3_InvalidateBlur));
            viewPage.scroller = new RecyclerListViewScroller(viewPage.listView);
            viewPage.listView.setAllowStopHeaveOperations(true);
            viewPage.listView.setAccessibilityEnabled(false);
            viewPage.listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
            viewPage.listView.setClipToPadding(false);
            viewPage.listView.setPivotY(0);
            viewPage.dialogsItemAnimator = new DialogsItemAnimator(viewPage.listView) {
                @Override
                public void onRemoveStarting(RecyclerView.ViewHolder item) {
                    super.onRemoveStarting(item);
                    if (viewPage.layoutManager.findFirstVisibleItemPosition() == 0) {
                        View v = viewPage.layoutManager.findViewByPosition(0);
                        if (v != null) {
                            v.invalidate();
                        }
                    }
                }
            };
            viewPage.listView.setVerticalScrollBarEnabled(true);
            viewPage.listView.setInstantClick(true);
            viewPage.layoutManager = new LinearLayoutManager(context) {
                private boolean fixOffset;

                @Override
                public void scrollToPositionWithOffset(int position, int offset) {
                    if (fixOffset) {
                        offset -= viewPage.listView.getPaddingTop();
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

                boolean lastDragging;

                @Override
                public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                    if (viewPage.listView.fastScrollAnimationRunning) {
                        return 0;
                    }
                    boolean isDragging = viewPage.listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;
                    if (isDragging != lastDragging) {
                        lastDragging = isDragging;
                    }
                    int measuredDy = dy;
                    int pTop = viewPage.listView.getPaddingTop();

                    if (viewPage.listView.getViewOffset() != 0 && dy > 0 && isDragging) {
                        float ty = (int) viewPage.listView.getViewOffset();
                        ty -= dy;
                        if (ty < 0) {
                            measuredDy = (int) ty;
                            ty = 0;
                        } else {
                            measuredDy = 0;
                        }
                        viewPage.listView.setViewsOffset(ty);
                    }

                    int scrolled = super.scrollVerticallyBy(measuredDy, recycler, state);
                    return scrolled;
                }

                @Override
                public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                        try {
                            super.onLayoutChildren(recycler, state);
                        } catch (IndexOutOfBoundsException e) {
                            throw new RuntimeException("Inconsistency detected. " + "dialogsListIsFrozen=" + dialogsListFrozen + " lastUpdateAction=" + debugLastUpdateAction);
                        }
                    } else {
                        try {
                            super.onLayoutChildren(recycler, state);
                        } catch (IndexOutOfBoundsException e) {
                            FileLog.e(e);
                            AndroidUtilities.runOnUIThread(() -> viewPage.dialogsAdapter.notifyDataSetChanged());
                        }
                    }
                }
            };
            viewPage.layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            viewPage.listView.setLayoutManager(viewPage.layoutManager);
            viewPage.listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
            viewPage.addView(viewPage.listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPage.listView.setOnItemClickListener((view, position, x, y) -> {
                if (view instanceof GraySectionCell)
                    return;
                if (view instanceof SavedChannelCell && !actionBar.isActionModeShowed()) {
                    SavedChannelCell dialogCell = (SavedChannelCell) view;
                    AndroidUtilities.rectTmp.set(
                            dialogCell.avatarImage.getImageX(), dialogCell.avatarImage.getImageY(),
                            dialogCell.avatarImage.getImageX2(), dialogCell.avatarImage.getImageY2()
                    );
                }
                onItemClick(view, position, viewPage.dialogsAdapter, x, y);
            });
            viewPage.listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
                @Override
                public boolean onItemClick(View view, int position, float x, float y) {
                    return onItemLongClick(view, position, x, y, viewPage.dialogsAdapter);
                }

                @Override
                public void onMove(float dx, float dy) {
                    if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        movePreviewFragment(dy);
                    }
                }

                @Override
                public void onLongClickRelease() {
                    if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        finishPreviewFragment();
                    }
                }
            });
            viewPage.swipeController = new SwipeController(viewPage);

            viewPage.itemTouchhelper = new ItemTouchHelper(viewPage.swipeController);
            viewPage.itemTouchhelper.attachToRecyclerView(viewPage.listView);

            viewPage.listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                private boolean wasManualScroll;

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        wasManualScroll = false;
                        disableActionBarScrolling = false;
                        if (waitingForScrollFinished) {
                            waitingForScrollFinished = false;
                            if (updatePullAfterScroll) {
                                updatePullAfterScroll = false;
                            }
                            viewPage.dialogsAdapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (contentView != null) {
                        contentView.updateBlurContent();
                    }
                    viewPage.dialogsItemAnimator.onListScroll(-dy);
                    int firstVisiblePosition = -1;
                    int lastVisiblePosition = -1;
                    for (int i = 0; i < recyclerView.getChildCount(); i++) {
                        int position = recyclerView.getChildAdapterPosition(recyclerView.getChildAt(i));
                        if (position >= 0) {
                            if (lastVisiblePosition == -1 || position > lastVisiblePosition) {
                                lastVisiblePosition = position;
                            }
                            if (firstVisiblePosition == -1 || position < firstVisiblePosition) {
                                firstVisiblePosition = position;
                            }
                        }
                    }
                    invalidateScrollY = true;
                    if (fragmentView != null) {
                        fragmentView.invalidate();
                    }
                    if (wasManualScroll && recyclerView.getChildCount() > 0) {
                        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition);
                            if (holder != null && holder.getAdapterPosition() >= 0) {
                                int firstViewTop = 0;
                                if (holder != null) {
                                    firstViewTop = holder.itemView.getTop();
                                }
                            }
                        }
                    }
                    if (recyclerView == viewPage.listView && actionBar != null && !actionBar.isActionModeShowed() && !disableActionBarScrolling) {
                        float currentTranslation = scrollYOffset;
                        invalidateScrollY = true;
                        if (fragmentView != null) {
                            fragmentView.invalidate();
                        }
                    }
                    if (fragmentView != null) {
                        blur3_InvalidateBlur();
                    }
                    final int topIndex = 0;
                    final View topChild = viewPage.listView.getChildAt(topIndex);
                    final int firstViewTop = topChild != null ? topChild.getTop() : 0;
                    final boolean shadowVisible = !(firstVisiblePosition <= topIndex && (firstViewTop - scrollYOffset + dp(5)) >= viewPage.listView.getPaddingTop());
                    animatorShadowVisible.setValue(shadowVisible, true);
                    if (dy != 0) {
                        if (scrollableViewNoiseSuppressor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                        }
                    }
                }
            });

            viewPage.dialogsAdapter = new SavedChannelsAdapter(this, context, 0, 0, false, selectedDialogs, currentAccount, requestPeerType) {
                @Override
                public void notifyDataSetChanged() {
                    try {
                        super.notifyDataSetChanged();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                @Override
                public void onButtonClicked(SavedChannelCell dialogCell) {
                    if (dialogCell.getMessage() != null) {
                        TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(-dialogCell.getDialogId(), MessageObject.getTopicId(currentAccount, dialogCell.getMessage().messageOwner, true));
                        if (topic != null) {
                            ForumUtilities.openTopic(SavedChannelsActivity.this, -dialogCell.getDialogId(), topic, 0);
                        }
                    }
                }

                @Override
                public void onButtonLongPress(SavedChannelCell dialogCell) {
                    onItemLongClick(dialogCell, viewPage.listView.getChildAdapterPosition(dialogCell), 0, 0, viewPage.dialogsAdapter);
                }

                @Override
                public void onCreateGroupForThisClick() {
                    createGroupForThis();
                }

                @Override
                protected void onOpenBot(TLRPC.User bot) {
                    MessagesController.getInstance(currentAccount).openApp(bot, 0);
                }
            };
            viewPage.dialogsAdapter.setRecyclerListView(viewPage.listView);

            if (AndroidUtilities.isTablet() && openedDialogId.dialogId != 0) {
                viewPage.dialogsAdapter.setOpenedDialogId(openedDialogId.dialogId);
            }
            viewPage.listView.setAdapter(viewPage.dialogsAdapter);

            viewPage.listView.setEmptyView(viewPage.progressView);
            viewPage.scrollHelper = new RecyclerAnimationScrollHelper(viewPage.listView, viewPage.layoutManager);
            viewPage.scrollHelper.forceUseStableId = true;
            viewPage.scrollHelper.isDialogs = true;
            viewPage.scrollHelper.setScrollListener(() -> {
                invalidateScrollY = true;
                fragmentView.invalidate();
            });

            savedChannelsMainAdapter = viewPage.dialogsAdapter;
        }

        if (savedChannelsMainAdapter != null) {
            savedChannelsMainAdapter.loadChats();
            doRefresh();
        }

        topBubblesFadeView = new DialogsActivityTopBubblesFadeView(context);
        topBubblesFadeView.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentView.addView(topBubblesFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP));

        topPanelLayout = new DialogsActivityTopPanelLayout(context);
        topPanelLayout.setOnAnimatedHeightChangedListener(() -> {
            viewPage.listView.requestLayout();
            updateContextViewPosition();
        });

        BlurredBackgroundDrawable topPanelLayoutBackground = iBlur3FactoryLiquidGlass.create(topPanelLayout,
            BlurredBackgroundProviderImpl.topPanel(resourceProvider));


        topPanelLayoutBackground.setRadius(dp(24));
        topPanelLayoutBackground.setPadding(dp(7));
        topPanelLayout.setPadding(dp(11), dp(21), dp(11), dp(21));
        topPanelLayout.setBlurredBackground(topPanelLayoutBackground);

        fragmentLocationContextViewWrapper = new FrameLayout(context);
        topPanelLayout.addView(fragmentLocationContextViewWrapper);
        topPanelLayout.setPriority(fragmentLocationContextViewWrapper, 5);
        topPanelLayout.setDebugName(fragmentLocationContextViewWrapper, "fragment location");
        topPanelLayout.setViewVisible(fragmentLocationContextViewWrapper, true, false);

        fragmentContextViewWrapper = new FrameLayout(context);
        topPanelLayout.addView(fragmentContextViewWrapper);
        topPanelLayout.setPriority(fragmentContextViewWrapper, 4);
        topPanelLayout.setDebugName(fragmentContextViewWrapper, "fragment context");
        topPanelLayout.setViewVisible(fragmentContextViewWrapper, true, false);

        fragmentLocationContextView = new FragmentContextView(context, this, true) {
            @Override
            public void setVisibility(int visibility) {
                topPanelLayout.setViewVisible(fragmentLocationContextViewWrapper, visibility == VISIBLE);
            }
        };
        fragmentLocationContextView.isInsideBubble = true;
        fragmentLocationContextViewWrapper.addView(fragmentLocationContextView);

        fragmentContextView = new FragmentContextView(context, this, false) {
            @Override
            public void setVisibility(int visibility) {
                topPanelLayout.setViewVisible(fragmentContextViewWrapper, visibility == VISIBLE);
            }
        };
        fragmentContextView.isInsideBubble = true;
        fragmentContextViewWrapper.addView(fragmentContextView);

        //if (!onlySelect || initialDialogsType == DIALOGS_TYPE_FORWARD) {
            final FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            if (inPreviewMode) {
                layoutParams.topMargin = AndroidUtilities.statusBarHeight;
            }
            contentView.addView(actionBar, layoutParams);
        //}
        animatedStatusView = new AnimatedStatusView(context, 20, 60);
        contentView.addView(animatedStatusView, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.TOP));

        undoViewIndex = contentView.getChildCount();
        undoView[0] = null;
        undoView[1] = null;

        if (hasMainTabs) {
            actionBar.getTitlesContainer().setTranslationX(dp(4));
            actionBar.setTitleColor(getThemedColor(Theme.key_telegram_color_dialogsLogo));
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100)));
        }
        blurredView.setFocusable(false);
        blurredView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurredView.setOnClickListener(e -> {
            finishPreviewFragment();
        });
        blurredView.setVisibility(View.GONE);
        contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBarDefaultPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FilesMigrationService.checkBottomSheet(this);
        }
        actionBar.setDrawBlurBackground(contentView);

        dialogsActivityStatusLayout = new DialogsActivityStatusLayout(context);
        // contentView.addView(dialogsActivityStatusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        if (topPanelLayout != null) {
            contentView.addView(topPanelLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -14, 0, 0));
        }

        checkUi_menuItems();
        checkUi_mainTabsVisible();
        checkUi_forwardCommentFieldVisible();

        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }

    public void showSelectStatusDialog() {
        if (selectAnimatedEmojiDialog != null || SharedConfig.isAppLocked()) {
            return;
        }
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        int xoff = 0, yoff = 0;
        boolean hasEmoji = false;
        SimpleTextView actionBarTitle = actionBar.getTitleTextView();
        if (actionBarTitle != null && actionBarTitle.getRightDrawable() != null) {
            AndroidUtilities.rectTmp2.set(actionBarTitle.getRightDrawable().getBounds());
            AndroidUtilities.rectTmp2.offset((int) actionBarTitle.getX(), (int) actionBarTitle.getY());
            yoff = -(actionBar.getHeight() - AndroidUtilities.rectTmp2.centerY()) - dp(16);
            xoff = AndroidUtilities.rectTmp2.centerX() - dp(16);
            xoff += dp(4);
            if (animatedStatusView != null) {
                animatedStatusView.translate(AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
            }
        }
        SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(this, getContext(), true, xoff, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, getResourceProvider()) {
            @Override
            protected boolean willApplyEmoji(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (gift != null) {
                    final TL_stars.SavedStarGift savedStarGift = StarsController.getInstance(currentAccount).findUserStarGift(gift.id);
                    return savedStarGift == null || MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) >= 2;
                }
                return true;
            }

            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                final TLRPC.EmojiStatus emojiStatus;
                if (documentId == null) {
                    emojiStatus = new TLRPC.TL_emojiStatusEmpty();
                } else if (gift != null) {
                    final TL_stars.SavedStarGift savedStarGift = StarsController.getInstance(currentAccount).findUserStarGift(gift.id);
                    if (savedStarGift != null && MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) < 2) {
                        MessagesController.getGlobalMainSettings().edit().putInt("statusgiftpage", MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) + 1).apply();
                        new StarGiftSheet(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), resourceProvider)
                            .set(savedStarGift, null)
                            .setupWearPage()
                            .show();
                        if (popup[0] != null) {
                            selectAnimatedEmojiDialog = null;
                            popup[0].dismiss();
                        }
                        return;
                    }
                    final TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                    status.collectible_id = gift.id;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                } else {
                    final TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                    status.document_id = documentId;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                }
                getMessagesController().updateEmojiStatus(emojiStatus, gift);
                if (documentId != null) {
                    animatedStatusView.animateChange(ReactionsLayoutInBubble.VisibleReaction.fromCustomEmoji(documentId));
                }
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        if (user != null && DialogObject.getEmojiStatusUntil(user.emoji_status) > 0) {
            popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        }
        popupLayout.setSaveState(1);
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        popup[0].showAsDropDown(actionBar, dp(16), yoff, Gravity.TOP);
        popup[0].dimBehind();
    }

    private int shiftDp = -4;

    private void createGroupForThis() {
        AlertDialog progress = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        if (requestPeerType instanceof TLRPC.TL_requestPeerTypeBroadcast) {
            Bundle args = new Bundle();
            args.putInt("step", 0);
            if (requestPeerType.has_username != null) {
                args.putBoolean("forcePublic", requestPeerType.has_username);
            }
            ChannelCreateActivity fragment = new ChannelCreateActivity(args);
            fragment.setOnFinishListener((fragment2, chatId) -> {
                Utilities.doCallbacks(
                        next -> {
                            TLRPC.Chat chat = getMessagesController().getChat(chatId);
                        },
                        next -> {
                            progress.showDelayed(150);
                            if (requestPeerType.bot_participant != null && requestPeerType.bot_participant) {
                                TLRPC.User bot = getMessagesController().getUser(requestPeerBotId);
                                getMessagesController().addUserToChat(chatId, bot, 0, null, SavedChannelsActivity.this, false, next, err -> {
                                    next.run();
                                    return true;
                                });
                            } else {
                                next.run();
                            }
                        },
                        next -> {
                            if (requestPeerType.bot_admin_rights != null) {
                                TLRPC.User bot = getMessagesController().getUser(requestPeerBotId);
                                getMessagesController().setUserAdminRole(chatId, bot, requestPeerType.bot_admin_rights, null, false, SavedChannelsActivity.this, !(requestPeerType.bot_participant != null && requestPeerType.bot_participant), true, null, next, err -> {
                                    next.run();
                                    return true;
                                });
                            } else {
                                next.run();
                            }
                        },
                        next -> {
                            if (requestPeerType.user_admin_rights != null) {
                                TLRPC.Chat chat = getMessagesController().getChat(chatId);
                                getMessagesController().setUserAdminRole(chatId, getAccountInstance().getUserConfig().getCurrentUser(), ChatRightsEditActivity.rightsOR(chat.admin_rights, requestPeerType.user_admin_rights), null, true, SavedChannelsActivity.this, false, true, null, next, err -> {
                                    next.run();
                                    return true;
                                });
                            } else {
                                next.run();
                            }
                        },
                        next -> {
                            progress.dismiss();
                            getMessagesController().loadChannelParticipants(chatId);
                            SavedChannelsActivity.this.removeSelfFromStack();
                            fragment.removeSelfFromStack();
                            fragment2.finishFragment();
                        }
                );
            });
            presentFragment(fragment);
        } else if (requestPeerType instanceof TLRPC.TL_requestPeerTypeChat) {
            Bundle args = new Bundle();
            long[] array;
            if (requestPeerType.bot_participant != null && requestPeerType.bot_participant) {
                array = new long[]{getUserConfig().getClientUserId(), requestPeerBotId};
            } else {
                array = new long[]{getUserConfig().getClientUserId()};
            }
            args.putLongArray("result", array);
            args.putInt("chatType", requestPeerType.forum != null && requestPeerType.forum ? ChatObject.CHAT_TYPE_FORUM : ChatObject.CHAT_TYPE_MEGAGROUP);
            args.putBoolean("canToggleTopics", false);
            GroupCreateFinalActivity activity = new GroupCreateFinalActivity(args);
            activity.setDelegate(new GroupCreateFinalActivity.GroupCreateFinalActivityDelegate() {
                @Override
                public void didStartChatCreation() {
                }

                @Override
                public void didFailChatCreation() {
                }

                @Override
                public void didFinishChatCreation(GroupCreateFinalActivity fragment, long chatId) {
                    BaseFragment[] lastFragments = new BaseFragment[]{fragment, null};
                    Utilities.doCallbacks(
                            next -> {
                                if (requestPeerType.has_username != null && requestPeerType.has_username) {
                                    Bundle args = new Bundle();
                                    args.putInt("step", 1);
                                    args.putLong("chat_id", chatId);
                                    args.putBoolean("forcePublic", requestPeerType.has_username);
                                    ChannelCreateActivity fragment2 = new ChannelCreateActivity(args);
                                    fragment2.setOnFinishListener((_fragment, _chatId) -> next.run());
                                    presentFragment(fragment2);
                                    lastFragments[1] = fragment2;
                                } else {
                                    next.run();
                                }
                            },
                            next -> {
                            },
                            next -> {
                                progress.showDelayed(150);
                                if (requestPeerType.bot_participant != null && requestPeerType.bot_participant) {
                                    TLRPC.User bot = getMessagesController().getUser(requestPeerBotId);
                                    getMessagesController().addUserToChat(chatId, bot, 0, null, SavedChannelsActivity.this, false, next, err -> {
                                        next.run();
                                        return true;
                                    });
                                } else {
                                    next.run();
                                }
                            },
                            next -> {
                                if (requestPeerType.bot_admin_rights != null) {
                                    TLRPC.User bot = getMessagesController().getUser(requestPeerBotId);
                                    getMessagesController().setUserAdminRole(chatId, bot, requestPeerType.bot_admin_rights, null, false, SavedChannelsActivity.this, !(requestPeerType.bot_participant != null && requestPeerType.bot_participant), true, null, next, err -> {
                                        next.run();
                                        return true;
                                    });
                                } else {
                                    next.run();
                                }
                            },
                            next -> {
                                if (requestPeerType.user_admin_rights != null) {
                                    TLRPC.Chat chat = getMessagesController().getChat(chatId);
                                    getMessagesController().setUserAdminRole(chatId, getAccountInstance().getUserConfig().getCurrentUser(), ChatRightsEditActivity.rightsOR(chat.admin_rights, requestPeerType.user_admin_rights), null, false, SavedChannelsActivity.this, false, true, null, next, err -> {
                                        next.run();
                                        return true;
                                    });
                                } else {
                                    next.run();
                                }
                            },
                            next -> {
                                progress.dismiss();
                                getMessagesController().loadChannelParticipants(chatId);
                                SavedChannelsActivity.this.removeSelfFromStack();
                                if (lastFragments[1] != null) {
                                    lastFragments[0].removeSelfFromStack();
                                    lastFragments[1].finishFragment();
                                } else {
                                    lastFragments[0].finishFragment();
                                }
                            }
                    );
                }
            });
            presentFragment(activity);
        }
    }

    private void updateContextViewPosition() {
        float totalOffset;
        totalOffset = scrollYOffset;

        float filtersTabHeight = 0;
        float filtersTabVisibility = 0;

        float topPanelsHeight = 0;
        float topPanelsVisibility = 0;
        float fadeViewT = totalOffset;

        if (topPanelLayout != null) {
            topPanelLayout.setTranslationY(lerp(
                totalOffset,
                -dp(3),
                0));
            topPanelsVisibility = topPanelLayout.getMetadata().getTotalVisibility();
            topPanelsHeight = topPanelLayout.getAnimatedHeightWithPadding(0);
        }

        if (topBubblesFadeView != null) {
            topBubblesFadeView.setTranslationY(fadeViewT);
            final float s = lerp(dp(7), dp(50), Math.min(topPanelsVisibility, filtersTabVisibility));
            topBubblesFadeView.setPosition(s, Math.min(dp(40), topPanelsHeight + filtersTabHeight - s));
            topBubblesFadeView.setAlpha(Math.max(filtersTabVisibility, topPanelsVisibility));
        }
    }


    @Override
    protected void onPanTranslationUpdate(float y) {
        panTranslationY = y;
        viewPage.setTranslationY(y);
        actionBar.setTranslationY(y);
    }

    @Override
    public void finishFragment() {
        super.finishFragment();
        if (filterOptions != null) {
            filterOptions.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (parentLayout != null && !parentLayout.isInPreviewMode() && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
        viewPage.dialogsAdapter.notifyDataSetChanged();
        getMediaDataController().checkStickers(MediaDataController.TYPE_EMOJI);
        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        viewPage.dialogsAdapter.resume();
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public void onBottomOffsetChange(float offset) {
                if (undoView[0] != null && undoView[0].getVisibility() == View.VISIBLE) {
                    return;
                }
            }

            @Override
            public void onShow(Bulletin bulletin) {
                if (undoView[0] != null && undoView[0].getVisibility() == View.VISIBLE) {
                    undoView[0].hide(true, 2);
                }
            }

            @Override
            public int getTopOffset(int tag) {
                return (
                    (actionBar != null ? actionBar.getMeasuredHeight() : 0) +
                    (topPanelLayout != null ? topPanelLayout.getHeight() : 0)
                );
            }

            @Override
            public int getBottomOffset(int tag) {
                return calculateListViewPaddingBottom();
            }
        });
        updateVisibleRows(0, false);
        checkUi_mainTabsVisible();

        if (savedChannelsMainAdapter != null && System.currentTimeMillis() - lastRefreshTime >= 60_000) {
            doRefresh();
        }
    }

    @Override
    public boolean presentFragment(BaseFragment fragment) {
        boolean b = super.presentFragment(fragment);
        if (b) {
            viewPage.dialogsAdapter.pause();
        }
        Bulletin.hideVisible();
        return b;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (filterOptions != null) {
            filterOptions.dismiss();
        }
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        Bulletin.removeDelegate(this);

        viewPage.dialogsAdapter.pause();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasShownSheet()) {
            if (invoked) closeSheet();
            return false;
        } else if (filterOptions != null) {
            if (invoked) {
                filterOptions.dismiss();
                filterOptions = null;
            }
            return false;
        } else if (actionBar != null && actionBar.isActionModeShowed()) {
            if (invoked) {
                hideActionMode(true);
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        if (!isInPreviewMode() && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
        super.onBecomeFullyHidden();
        checkUi_mainTabsVisible();
    }

    public boolean addOrRemoveSelectedDialog(long did, View cell) {
        if (selectedDialogs.contains(did)) {
            selectedDialogs.remove(did);
            if (cell instanceof SavedChannelCell) {
                ((SavedChannelCell) cell).setChecked(false, true);
            } else if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(false, true);
            }
            return false;
        } else {
            selectedDialogs.add(did);
            if (cell instanceof SavedChannelCell) {
                ((SavedChannelCell) cell).setChecked(true, true);
            } else if (cell instanceof ProfileSearchCell) {
                ((ProfileSearchCell) cell).setChecked(true, true);
            }
            return true;
        }
    }

    private void onItemClick(View view, int position, RecyclerListView.Adapter adapter, float x, float y) {
        if (getParentActivity() == null) {
            return;
        }
        long dialogId = 0;
        long topicId = 0;
        int message_id = 0;
        MessageObject msg = null;
        boolean isGlobalSearch = false;
        int folderId = 0;
        int filterId = 0;
        if (adapter instanceof SavedChannelsAdapter) {
            SavedChannelsAdapter dialogsAdapter = (SavedChannelsAdapter) adapter;
            TLObject object = dialogsAdapter.getItem(position);
            if (object instanceof TLRPC.User) {
                dialogId = ((TLRPC.User) object).id;
            } else if (object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                dialogId = -chat.id;
                if (actionBar.isActionModeShowed(SAVED_CHANNEL_ACTION_MODE_TAG)) {
                    showOrUpdateActionMode(dialogId, view);
                    return;
                }
            } else if (object instanceof TLRPC.TL_recentMeUrlChat) {
                dialogId = -((TLRPC.TL_recentMeUrlChat) object).chat_id;
            } else if (object instanceof TLRPC.TL_recentMeUrlUser) {
                dialogId = ((TLRPC.TL_recentMeUrlUser) object).user_id;
            } else if (object instanceof TLRPC.TL_recentMeUrlChatInvite) {
                TLRPC.TL_recentMeUrlChatInvite chatInvite = (TLRPC.TL_recentMeUrlChatInvite) object;
                TLRPC.ChatInvite invite = chatInvite.chat_invite;
                if (invite.chat == null && (!invite.channel || invite.megagroup) || invite.chat != null && (!ChatObject.isChannel(invite.chat) || invite.chat.megagroup)) {
                    String hash = chatInvite.url;
                    int index = hash.indexOf('/');
                    if (index > 0) {
                        hash = hash.substring(index + 1);
                    }
                    showDialog(new JoinGroupAlert(getParentActivity(), invite, hash, SavedChannelsActivity.this, null));
                    return;
                } else {
                    if (invite.chat != null) {
                        dialogId = -invite.chat.id;
                    } else {
                        return;
                    }
                }
            } else if (object instanceof TLRPC.TL_recentMeUrlStickerSet) {
                TLRPC.StickerSet stickerSet = ((TLRPC.TL_recentMeUrlStickerSet) object).set.set;
                TLRPC.TL_inputStickerSetID set = new TLRPC.TL_inputStickerSetID();
                set.id = stickerSet.id;
                set.access_hash = stickerSet.access_hash;
                showDialog(new StickersAlert(getParentActivity(), SavedChannelsActivity.this, set, null, null, false));
                return;
            } else if (object instanceof TLRPC.TL_recentMeUrlUnknown) {
                return;
            } else {
                return;
            }
        }

        if (dialogId == 0) {
            return;
        }

        Bundle args = new Bundle();
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
            args.putBoolean("is_saved_channel", true);
        }
        if (message_id != 0) {
            args.putInt("message_id", message_id);
        } else if (!isGlobalSearch) {
            closeSearch();
        }
        args.putInt("dialog_folder_id", folderId);
        args.putInt("dialog_filter_id", filterId);
        if (AndroidUtilities.isTablet() && !getMessagesController().isForum(dialogId)) {
            if (openedDialogId.dialogId == dialogId) {
                if (getParentActivity() instanceof LaunchActivity) {
                    LaunchActivity launchActivity = (LaunchActivity) getParentActivity();
                    List<BaseFragment> rightFragments = launchActivity.getRightActionBarLayout().getFragmentStack();
                    if (!rightFragments.isEmpty()) {
                        if (rightFragments.size() == 1 && rightFragments.get(rightFragments.size() - 1) instanceof ChatActivity) {
                            ((ChatActivity) rightFragments.get(rightFragments.size() - 1)).onPageDownClicked();
                        } else if (rightFragments.size() == 2) {
                            launchActivity.getRightActionBarLayout().closeLastFragment();
                        } else if (getParentActivity() instanceof LaunchActivity) {
                            BaseFragment first = rightFragments.get(0);
                            rightFragments.clear();
                            rightFragments.add(first);
                            launchActivity.getRightActionBarLayout().rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                        }
                    }
                }
                return;
            }
        }
        if (dialogId == getUserConfig().getClientUserId() && getMessagesController().savedViewAsChats) {
            args = new Bundle();
            args.putLong("dialog_id", UserConfig.getInstance(currentAccount).getClientUserId());
            args.putInt("type", MediaActivity.TYPE_MEDIA);
            args.putInt("start_from", SharedMediaLayout.TAB_SAVED_DIALOGS);
            if (sharedMediaPreloader == null) {
                sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
            }
            MediaActivity mediaActivity = new MediaActivity(args, sharedMediaPreloader);
            presentFragment(mediaActivity);
        } else {
            if (getMessagesController().checkCanOpenChat(args, SavedChannelsActivity.this)) {
                TLRPC.Chat chat = getMessagesController().getChat(-dialogId);

                if (chat != null && (chat.monoforum || chat.forum) && topicId == 0) {
                    if (chat.monoforum) {
                        args.putInt("chatMode", ChatActivity.MODE_SUGGESTIONS);
                        args.putBoolean("isSubscriberSuggestions", !ChatObject.canManageMonoForum(currentAccount, chat));

                        ChatActivity activity = new ChatActivity(args);
//                            ForumUtilities.applyTopic(activity, MessagesStorage.TopicKey.of(-chat.id, getMessagesController().getForumLastTopicId(chat.id)));
                        presentFragment(highlightFoundQuote(activity, msg));
                    } else if (ChatObject.areTabsEnabled(chat)) {
                        ChatActivity activity = new ChatActivity(args);
                        ForumUtilities.applyTopic(activity, MessagesStorage.TopicKey.of(-chat.id, getMessagesController().getForumLastTopicId(chat.id)));
                        presentFragment(activity);
                    } else {
                        presentFragment(new TopicsFragment(args));
                    }
                } else {
                    ChatActivity chatActivity = new ChatActivity(args);
                    if (topicId != 0) {
                        ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(dialogId, topicId));
                    }
                    if (adapter instanceof SavedChannelsAdapter && DialogObject.isUserDialog(dialogId)) {
                        TLRPC.Document sticker = getMediaDataController().getGreetingsSticker();
                        if (sticker != null) {
                            chatActivity.setPreloadedSticker(sticker, true);
                        }
                    }
                    presentFragment(highlightFoundQuote(chatActivity, msg));
                }
            }
        }
    }

    public static ChatActivity highlightFoundQuote(ChatActivity chatActivity, MessageObject message) {
        if (message != null && message.hasHighlightedWords()) {
            try {
                CharSequence text = null;
                if (!TextUtils.isEmpty(message.caption)) {
                    text = message.caption;
                } else {
                    text = message.messageText;
                }
                CharSequence highlighted = AndroidUtilities.highlightText(text, message.highlightedWords, null);
                if (highlighted instanceof SpannableStringBuilder) {
                    SpannableStringBuilder spannedHighlighted = (SpannableStringBuilder) highlighted;
                    ForegroundColorSpanThemable[] spans = spannedHighlighted.getSpans(0, spannedHighlighted.length(), ForegroundColorSpanThemable.class);
                    if (spans.length > 0) {
                        int start = spannedHighlighted.getSpanStart(spans[0]);
                        int end = spannedHighlighted.getSpanEnd(spans[0]);
                        for (int i = 1; i < spans.length; ++i) {
                            int sstart = spannedHighlighted.getSpanStart(spans[i]);
                            int send = spannedHighlighted.getSpanStart(spans[i]);
                            if (sstart == end) {
                                end = send;
                            } else if (sstart > end) {
                                boolean whitespace = true;
                                for (int j = end; j <= sstart; ++j) {
                                    if (!Character.isWhitespace(spannedHighlighted.charAt(j))) {
                                        whitespace = false;
                                        break;
                                    }
                                }
                                if (whitespace) {
                                    end = send;
                                }
                            }
                        }
                        chatActivity.setHighlightQuote(message.getRealId(), text.subSequence(start, end).toString(), start);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return chatActivity;
    }

    public void setOpenedDialogId(long dialogId, long topicId) {
        openedDialogId.dialogId = dialogId;
        openedDialogId.topicId = topicId;

        if (viewPage.isDefaultDialogType() && AndroidUtilities.isTablet()) {
            viewPage.dialogsAdapter.setOpenedDialogId(openedDialogId.dialogId);
        }
        updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
    }

    private boolean onItemLongClick(View view, int position, float x, float y, RecyclerListView.Adapter adapter) {
        if (getParentActivity() == null || view instanceof DialogsHintCell) {
            return false;
        }

        if (!actionBar.isActionModeShowed() && !AndroidUtilities.isTablet() && view instanceof SavedChannelCell && !getMessagesController().isForum(((SavedChannelCell) view).getDialogId())) {
            SavedChannelCell cell = (SavedChannelCell) view;
            if (cell.isPointInsideAvatar(x, y)) {
                return showChatPreview(cell);
            }
        }
        SavedChannelsAdapter dialogsAdapter = (SavedChannelsAdapter) adapter;

        Object item = dialogsAdapter.getItem(position);
        if (!(item instanceof TLRPC.Chat)) {
            return false;
        }
        TLRPC.Chat chat = (TLRPC.Chat) item;
        showOrUpdateActionMode(-chat.id, view);
        return true;
    }

    public boolean showChatPreview(SavedChannelCell cell) {
        long dialogId = cell.getDialogId();
        Bundle args = new Bundle();
        int message_id = cell.getMessageId();
        long did = dialogId;
        if (message_id != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            if (chat != null && chat.migrated_to != null) {
                args.putLong("migrated_to", did);
                did = -chat.migrated_to.channel_id;
            }
        }
        args.putLong("chat_id", -did);
        if (message_id != 0) {
            args.putInt("message_id", message_id);
        }

        final ArrayList<String> dialogUsernameArray = new ArrayList<>();
        dialogUsernameArray.add(cell.getUsername());

        final ActionBarPopupWindow.ActionBarPopupWindowLayout[] previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout[1];

        int flags = ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_SHOWN_FROM_BOTTOM;

        final ChatActivity[] chatActivity = new ChatActivity[1];
        previewMenu[0] = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert2, getResourceProvider(), flags);

        ActionBarMenuSubItem deleteItem = new ActionBarMenuSubItem(getParentActivity(), false, true);
        deleteItem.setIconColor(getThemedColor(Theme.key_text_RedRegular));
        deleteItem.setTextColor(getThemedColor(Theme.key_text_RedBold));
        deleteItem.setSelectorColor(Theme.multAlpha(getThemedColor(Theme.key_text_RedBold), .12f));
        deleteItem.setTextAndIcon(LocaleController.getString(R.string.Delete), R.drawable.msg_delete);
        deleteItem.setMinimumWidth(160);
        deleteItem.setOnClickListener(e -> {
            performSelectedDialogsAction(dialogUsernameArray, delete);
            finishPreviewFragment();
        });
        previewMenu[0].addView(deleteItem);

        if (getMessagesController().checkCanOpenChat(args, SavedChannelsActivity.this)) {
            prepareBlurBitmap();
            parentLayout.setHighlightActionButtons(true);
            if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                presentFragmentAsPreview(chatActivity[0] = new ChatActivity(args));
            } else {
                presentFragmentAsPreviewWithMenu(chatActivity[0] = new ChatActivity(args), previewMenu[0]);
                if (chatActivity[0] != null) {
                    chatActivity[0].allowExpandPreviewByClick = true;
                    try {
                        chatActivity[0].getAvatarContainer().getAvatarImageView().performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                    } catch (Exception ignore) {
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean waitingForDialogsAnimationEnd(ViewPage viewPage) {
        return viewPage.dialogsItemAnimator.isRunning();
    }

    private void checkAnimationFinished() {
        AndroidUtilities.runOnUIThread(() -> {
//            if (viewPages != null && folderId != 0 && (frozenDialogsList == null || frozenDialogsList.isEmpty())) {
//                for (int a = 0; a < viewPages.length; a++) {
//                    viewPages[a].listView.setEmptyView(null);
//                    viewPages[a].progressView.setVisibility(View.INVISIBLE);
//                }
//                finishFragment();
//            }
            setDialogsListFrozen(false);
            updateDialogIndices();
        }, 300);
    }

    private void setScrollY(float value) {
        {
            int glowOffset = viewPage.listView.getPaddingTop() + (int) value;
            viewPage.listView.setTopGlowOffset(glowOffset);
        }
        if (fragmentView == null || value == scrollYOffset) {
            return;
        }
        scrollYOffset = value;
        if (animatedStatusView != null) {
            animatedStatusView.translateY2((int) value);
            animatedStatusView.setAlpha(1f - -value / ActionBar.getCurrentActionBarHeight());
        }
        fragmentView.invalidate();
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 9.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 9.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 9.0f, 1.0f / 9.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(9, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
        checkUi_mainTabsVisible();
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
        checkUi_mainTabsVisible();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
        checkUi_mainTabsVisible();
    }

    private void resetScroll() {
        if (scrollYOffset == 0) {
            return;
        }

        final float target = 0;

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, SCROLL_Y, target));
        animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.setDuration(250);
        animatorSet.start();
    }

    private void hideActionMode(boolean animateCheck) {
        actionBar.hideActionMode();
        selectedDialogs.clear();
        if (actionBarColorAnimator != null) {
            actionBarColorAnimator.cancel();
            actionBarColorAnimator = null;
        }
        if (progressToActionMode == 0) {
            return;
        }
        float translateListHeight = 0;
        setScrollY(0);
        if (viewPage != null) {
            viewPage.listView.cancelClickRunnables(true);
        }
        translateListHeight = Math.max(0, dp(0) + scrollYOffset);
        float finalTranslateListHeight = translateListHeight;
        actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 0);
        actionBarColorAnimator.addUpdateListener(valueAnimator -> {
            viewPage.setTranslationY(finalTranslateListHeight * (1f - progressToActionMode));
            progressToActionMode = (float) valueAnimator.getAnimatedValue();
            for (int i = 0; i < actionBar.getChildCount(); i++) {
                if (actionBar.getChildAt(i).getVisibility() == View.VISIBLE && actionBar.getChildAt(i) != actionBar.getActionMode() && actionBar.getChildAt(i) != actionBar.getBackButton()) {
                    actionBar.getChildAt(i).setAlpha(1f - progressToActionMode);
                }
            }
            checkUi_itemBackButtonVisibility();
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        });
        actionBarColorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                actionBarColorAnimator = null;
                invalidateScrollY = true;
                fragmentView.invalidate();
                scrollAdditionalOffset = -(dp((0)) - finalTranslateListHeight);
                viewPage.setTranslationY(0);
                if (viewPage != null) {
                    viewPage.listView.requestLayout();
                }
                fragmentView.requestLayout();
            }
        });
        actionBarColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        actionBarColorAnimator.setDuration(200);
        actionBarColorAnimator.start();
        allowMoving = false;
        viewPage.dialogsAdapter.onReorderStateChanged(false);
        updateVisibleRows(MessagesController.UPDATE_MASK_REORDER | MessagesController.UPDATE_MASK_CHECK | (animateCheck ? MessagesController.UPDATE_MASK_CHAT : 0));
    }

    private static final int pin = 200;
    private static final int delete = 201;
    private static final int refresh = 202;
    private static final String SAVED_CHANNEL_ACTION_MODE_TAG = "savedChannels";

    private ActionBarMenuItem pinItem;
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem refreshItem;
    private ObjectAnimator refreshAnimator;
    private long lastRefreshTime;
    private NumberTextView selectedDialogsCountTextView;
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    private int canPinCount = 0;

    private void createActionMode() {
        if (actionBar.actionModeIsExist(SAVED_CHANNEL_ACTION_MODE_TAG)) {
            return;
        }
        ActionBarMenu actionMode = actionBar.createActionMode(false, SAVED_CHANNEL_ACTION_MODE_TAG);
        actionMode.setBackground(null);

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.bold());
        selectedDialogsCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        pinItem = actionMode.addItemWithWidth(pin, R.drawable.msg_pin, dp(54));
        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, dp(54), LocaleController.getString(R.string.Delete));

        actionModeViews.add(pinItem);
        actionModeViews.add(deleteItem);
    }

    private void showOrUpdateActionMode(long dialogId, View cell) {
        addOrRemoveSelectedDialog(dialogId, cell);
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            if (selectedDialogs.isEmpty()) {
                hideActionMode(true);
                return;
            }
            updateAnimated = true;
        } else {
            createActionMode();
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.setActionModeOverrideColor(getThemedColor(Theme.key_windowBackgroundWhite));
            actionBar.showActionMode();
            actionBar.setBackButtonDrawable(new BackDrawable(true));

            AnimatorSet animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View v = actionModeViews.get(a);
                v.setPivotY(ActionBar.getCurrentActionBarHeight() / 2f);
                AndroidUtilities.clearDrawableAnimation(v);
                animators.add(ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.1f, 1.0f));
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(200);
            animatorSet.start();

            if (actionBarColorAnimator != null) {
                actionBarColorAnimator.cancel();
            }
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 1f);
            float translateListHeight = 0;
            if (viewPage != null) {
                viewPage.listView.cancelClickRunnables(true);
            }
            translateListHeight = Math.max(0, scrollYOffset);
            if (translateListHeight != 0) {
                fragmentView.requestLayout();
            }
            float finalTranslateListHeight = translateListHeight;
            actionBarColorAnimator.addUpdateListener(valueAnimator -> {
                progressToActionMode = (float) valueAnimator.getAnimatedValue();
                viewPage.setTranslationY(-finalTranslateListHeight * progressToActionMode);
                for (int i = 0; i < actionBar.getChildCount(); i++) {
                    if (actionBar.getChildAt(i).getVisibility() == View.VISIBLE && actionBar.getChildAt(i) != actionBar.getActionMode() && actionBar.getChildAt(i) != actionBar.getBackButton()) {
                        actionBar.getChildAt(i).setAlpha(1f - progressToActionMode);
                    }
                }
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
                checkUi_itemBackButtonVisibility();
            });
            actionBarColorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    actionBarColorAnimator = null;
                    scrollAdditionalOffset = -finalTranslateListHeight;
                    viewPage.setTranslationY(0);
                    if (viewPage != null) {
                        viewPage.listView.requestLayout();
                    }
                    fragmentView.requestLayout();
                }
            });
            actionBarColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            actionBarColorAnimator.setDuration(200);
            actionBarColorAnimator.start();

            if (!getUserConfig().pinnedSavedChannels.isEmpty()) {
                if (savedChannelsMainAdapter != null) {
                    savedChannelsMainAdapter.onReorderStateChanged(true);
                }
                updateVisibleRows(MessagesController.UPDATE_MASK_REORDER);
            }
        }
        updateCounters(false);
        selectedDialogsCountTextView.setNumber(selectedDialogs.size(), updateAnimated);
    }

    private void updateCounters(boolean hide) {
        canPinCount = 0;
        if (hide || pinItem == null) {
            return;
        }
        if (savedChannelsMainAdapter != null) {
            List<String> selectedUsernames = savedChannelsMainAdapter.getSelectedUserNames();
            for (String userName : selectedUsernames) {
                if (!isSavedChannelPinned(userName)) {
                    canPinCount++;
                }
            }
        }
        pinItem.setVisibility(View.VISIBLE);
        if (canPinCount != 0) {
            pinItem.setIcon(R.drawable.msg_pin);
            pinItem.setContentDescription(LocaleController.getString(R.string.PinToTop));
        } else {
            pinItem.setIcon(R.drawable.msg_unpin);
            pinItem.setContentDescription(LocaleController.getString(R.string.UnpinFromTop));
        }
        deleteItem.setVisibility(View.VISIBLE);
    }

    private boolean isSavedChannelPinned(String userName) {
        return getUserConfig().pinnedSavedChannels.contains(userName);
    }

    private void pinSavedChannel(String userName, boolean pin) {
        if (pin) {
            getUserConfig().pinnedSavedChannels.add(userName);
        } else {
            getUserConfig().pinnedSavedChannels.remove(userName);
        }
        getUserConfig().saveConfig(true);
        if (savedChannelsMainAdapter != null) {
            savedChannelsMainAdapter.fixChatPosition(userName);
        }
    }

    private boolean isChatPinned(TLRPC.Chat chat) {
        if (chat == null || chat.username == null) {
            return false;
        }
        return getUserConfig().pinnedSavedChannels.contains(chat.username);
    }

    private void performSelectedDialogsAction(List<String> selectedUsernames, int action) {
        if (getParentActivity() == null) {
            return;
        }
        if (action == pin) {
            if (savedChannelsMainAdapter != null) {
                for (String userName : selectedUsernames) {
                    if (canPinCount != 0) {
                        if (!isSavedChannelPinned(userName)) {
                            pinSavedChannel(userName, true);
                        }
                    } else {
                        if (isSavedChannelPinned(userName)) {
                            pinSavedChannel(userName, false);
                        }
                    }
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        } else if (action == delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.formatString(R.string.DeleteFewChatsTitle, LocaleController.formatPluralString("ChatsSelected", selectedDialogs.size())));
            builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteFewChats));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog1, which) -> {
                if (savedChannelsMainAdapter != null) {
                    UserConfig userConfig = getUserConfig();
                    userConfig.savedChannels.removeAll(selectedUsernames);
                    userConfig.pinnedSavedChannels.removeAll(selectedUsernames);
                    userConfig.saveConfig(true);
                    savedChannelsMainAdapter.removeItems(selectedUsernames);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                }
                hideActionMode(true);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
        hideActionMode(action != delete);
    }

    public void scrollToTop(boolean animated) {
        int position = 0;
        int offset = 0;
        if (animated) {
            viewPage.scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            viewPage.scrollHelper.scrollToPosition(position, offset, false, true);
            resetScroll();
        } else {
            viewPage.layoutManager.scrollToPositionWithOffset(position, offset);
            resetScroll();
        }
    }


    private void closeSearch() {
        if (AndroidUtilities.isTablet()) {
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
        }
    }

    protected RecyclerListView getListView() {
        return viewPage.listView;
    }

    public void createUndoView() {
        if (undoView[0] != null) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context) {
                @Override
                protected boolean canUndo() {
                    if (viewPage.dialogsItemAnimator.isRunning()) {
                        return false;
                    }
                    return true;
                }

                @Override
                protected void onRemoveDialogAction(long currentDialogId, int action) {
                    if (action == UndoView.ACTION_DELETE || action == UndoView.ACTION_DELETE_FEW) {
                        debugLastUpdateAction = 1;
                        setDialogsListFrozen(true);
                        if (frozenDialogsList != null) {
                            int selectedIndex = -1;
                            for (int i = 0; i < frozenDialogsList.size(); i++) {
                                if (-frozenDialogsList.get(i).id == currentDialogId) {
                                    selectedIndex = i;
                                    break;
                                }
                            }

                            if (selectedIndex >= 0) {
                                TLRPC.Chat removedChat = frozenDialogsList.remove(selectedIndex);
                                viewPage.dialogsAdapter.notifyDataSetChanged();
                                int finalSelectedIndex = selectedIndex;
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (frozenDialogsList != null) {
                                        if (finalSelectedIndex < 0 || finalSelectedIndex >= frozenDialogsList.size()) {
                                            return;
                                        }
                                        frozenDialogsList.add(finalSelectedIndex, removedChat);
                                        viewPage.updateList(true);
                                    }
                                });
                            } else {
                                setDialogsListFrozen(false);
                            }
                        }
                        checkAnimationFinished();
                    }
                }
            };

            FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8);
            lp.bottomMargin += navigationBarHeight + additionNavigationBarHeight;
            ((ContentView) fragmentView).addView(undoView[a], ++undoViewIndex, lp);
        }
    }

    @Nullable
    public UndoView getUndoView() {
        createUndoView();
        if (undoView[0] != null && undoView[0].getVisibility() == View.VISIBLE) {
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (filterOptions != null) {
            filterOptions.dismiss();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            long dialogId = (Long) args[0];
            List<MessageObject> messages = (List<MessageObject>) args[2];
            if (savedChannelsMainAdapter != null) {
                savedChannelsMainAdapter.messagesDidLoad(dialogId, messages);
            }
        } else if (id == NotificationCenter.topicsDidLoaded) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.dialogsUnreadReactionsCounterChanged) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.emojiLoaded) {
            {
                final RecyclerListView listView = viewPage.listView;
                if (listView != null) {
                    for (int a = 0; a < listView.getChildCount(); ++a) {
                        View child = listView.getChildAt(a);
                        if (child != null) {
                            child.invalidate();
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer mask = (Integer) args[0];
            updateVisibleRows(mask);
        } else if (id == NotificationCenter.openedChatChanged) {
            if (viewPage.isDefaultDialogType() && AndroidUtilities.isTablet()) {
                boolean close = (Boolean) args[2];
                long dialog_id = (Long) args[0];
                long topicId = (Long) args[1];
                if (close) {
                    if (dialog_id == openedDialogId.dialogId && topicId == openedDialogId.topicId) {
                        openedDialogId.dialogId = 0;
                        openedDialogId.topicId = 0;
                    }
                } else {
                    openedDialogId.dialogId = dialog_id;
                    openedDialogId.topicId = topicId;
                }
                viewPage.dialogsAdapter.setOpenedDialogId(openedDialogId.dialogId);
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
            if (fragmentView == null) {
                return;
            }
            long dialogId = (Long) args[0];
            TLRPC.User user = (TLRPC.User) args[1];
            TLRPC.Chat chat = (TLRPC.Chat) args[2];
            boolean revoke;
            boolean botBlock;
            if (user != null && user.bot) {
                revoke = false;
                botBlock = (Boolean) args[3];
            } else {
                revoke = (Boolean) args[3];
                botBlock = false;
            }

            Runnable deleteRunnable = () -> {
                if (chat != null) {
                    if (ChatObject.isNotInChat(chat)) {
                        getMessagesController().deleteDialog(dialogId, 0, revoke);
                    } else {
                        getMessagesController().deleteParticipantFromChat(-dialogId, getMessagesController().getUser(getUserConfig().getClientUserId()), null, revoke, revoke);
                    }
                } else {
                    getMessagesController().deleteDialog(dialogId, 0, revoke);
                    if (user != null && user.bot && botBlock) {
                        getMessagesController().blockPeer(user.id);
                    }
                }
                getMessagesController().checkIfFolderEmpty(0);
            };
            createUndoView();
            if (undoView[0] != null) {
                if (!ChatObject.isForum(chat)) {
                    UndoView undoView = getUndoView();
                    if (undoView != null) {
                        undoView.showWithAction(dialogId, revoke ? UndoView.ACTION_DELETE : UndoView.ACTION_LEAVE, deleteRunnable);
                    }
                } else {
                    deleteRunnable.run();
                }
            } else {
                deleteRunnable.run();
            }
        } else if (id == NotificationCenter.dialogTranslate) {
            long dialogId = (long) args[0];
            if (viewPage.listView != null) {
                for (int j = 0; j < viewPage.listView.getChildCount(); ++j) {
                    View child = viewPage.listView.getChildAt(j);
                    if (child instanceof SavedChannelCell) {
                        SavedChannelCell dialogCell = (SavedChannelCell) child;
                        if (dialogId == dialogCell.getDialogId()) {
                            dialogCell.buildLayout();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.savedChannelAdded) {
            if (savedChannelsMainAdapter != null) {
                savedChannelsMainAdapter.loadChats();
            }
        }
    }

    private void setDialogsListFrozen(boolean frozen, boolean notify) {
        if (dialogsListFrozen == frozen) {
            return;
        }
        if (frozen) {
            frozenDialogsList = new ArrayList<>(getDialogsArray());
        } else {
            frozenDialogsList = null;
        }
        dialogsListFrozen = frozen;
        viewPage.dialogsAdapter.setDialogsListFrozen(frozen);
        if (!frozen && notify) {
            if (viewPage.listView.isComputingLayout()) {
                viewPage.listView.post(() -> viewPage.dialogsAdapter.notifyDataSetChanged());
            } else {
                viewPage.dialogsAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setDialogsListFrozen(boolean frozen) {
        setDialogsListFrozen(frozen, true);
    }

    @NonNull
    public ArrayList<TLRPC.Chat> getDialogsArray() {
        if (savedChannelsMainAdapter != null) {
            return savedChannelsMainAdapter.getChats();
        }
        return new ArrayList<>();
    }

    public float getContactsAlpha() {
        return contactsAlpha;
    }

    public void animateContactsAlpha(float alpha) {
        if (contactsAlphaAnimator != null) {
            contactsAlphaAnimator.cancel();
        }

        contactsAlphaAnimator = ValueAnimator.ofFloat(contactsAlpha, alpha).setDuration(250);
        contactsAlphaAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        contactsAlphaAnimator.addUpdateListener(animation -> setContactsAlpha((float) animation.getAnimatedValue()));
        contactsAlphaAnimator.start();
    }

    public void setContactsAlpha(float alpha) {
        contactsAlpha = alpha;
        RecyclerListView listView = viewPage.listView;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View v = listView.getChildAt(i);
            if (v != null && listView.getChildAdapterPosition(v) >= viewPage.dialogsAdapter.getDialogsCount() + 1) {
                v.setAlpha(alpha);
            }
        }
    }

    public void setScrollDisabled(boolean disable) {
        LinearLayoutManager llm = (LinearLayoutManager) viewPage.listView.getLayoutManager();
        llm.setScrollDisabled(disable);
    }

    private void updateDialogIndices() {
        if (viewPage.getVisibility() != View.VISIBLE || viewPage.dialogsAdapter.getDialogsListIsFrozen()) {
            return;
        }
        viewPage.updateList(false);
    }

    private void updateVisibleRows(int mask) {
        updateVisibleRows(mask, true);
    }

    private void updateVisibleRows(int mask, boolean animated) {
        if ((dialogsListFrozen && (mask & MessagesController.UPDATE_MASK_REORDER) == 0) || isPaused) {
            return;
        }
        RecyclerListView list = viewPage.listView;
        if (viewPage.getVisibility() != View.VISIBLE) {
            return;
        }
        if (list == null || list.getAdapter() == null) {
            return;
        }
        if ((mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) != 0 || mask == 0) {
            viewPage.updateList(false);
            return;
        }
        int count = list.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = list.getChildAt(a);
            if (child instanceof SavedChannelCell) {
                SavedChannelCell cell = (SavedChannelCell) child;
                if ((mask & MessagesController.UPDATE_MASK_REORDER) != 0) {
                    cell.onReorderStateChanged(actionBar.isActionModeShowed(), true);
                    if (dialogsListFrozen) {
                        continue;
                    }
                }
                if ((mask & MessagesController.UPDATE_MASK_CHECK) != 0) {
                    cell.setChecked(false, (mask & MessagesController.UPDATE_MASK_CHAT) != 0);
                } else {
                    if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) != 0) {
                        if (viewPage.isDefaultDialogType() && AndroidUtilities.isTablet()) {
                            cell.setDialogSelected(cell.getDialogId() == openedDialogId.dialogId);
                        }
                    } else {
                        if (cell.update(mask, animated)) {
                            viewPage.updateList(false);
                            break;
                        }
                    }
                    if (selectedDialogs != null) {
                        cell.setChecked(selectedDialogs.contains(cell.getDialogId()), false);
                    }
                }
            }


            if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            } else if (child instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) child;
                cell.update(mask);
                if (selectedDialogs != null) {
                    cell.setChecked(selectedDialogs.contains(cell.getDialogId()), false);
                }
            }
            if (dialogsListFrozen) {
                continue;
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

    public boolean shouldShowNextButton() {
        return false;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            {
                RecyclerListView list = viewPage.listView;
                if (list != null) {
                    int count = list.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = list.getChildAt(a);
                        if (child instanceof ProfileSearchCell) {
                            ((ProfileSearchCell) child).update(0);
                        } else if (child instanceof SavedChannelCell) {
                            ((SavedChannelCell) child).update(0);
                        } else if (child instanceof UserCell) {
                            ((UserCell) child).update(0);
                        }
                    }
                }
            }
            if (actionBar != null) {
                actionBar.setPopupBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground), true);
                actionBar.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false, true);
                actionBar.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, true);
                actionBar.setPopupItemsSelectorColor(getThemedColor(Theme.key_dialogButtonSelector), true);
                actionBar.updateColors();
            }

            if (filterOptions != null) {
                filterOptions.updateColors();
            }
            if (blurredView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100)));
                }
            }

            iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            if (topPanelLayout != null) {
                topPanelLayout.updateColors();
            }
            if (topBubblesFadeView != null) {
                topBubblesFadeView.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            if (fragmentContextView != null) {
                fragmentContextView.updateColors();
            }
            if (fragmentLocationContextView != null) {
                fragmentLocationContextView.updateColors();
            }
        };

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        if (movingView != null) {
            arrayList.add(new ThemeDescription(movingView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }

        arrayList.add(new ThemeDescription(fragmentView, 0, null, actionBarDefaultPaint, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, cellDelegate, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, new Drawable[]{Theme.dialogs_holidayDrawable}, null, !hasMainTabs ? Theme.key_actionBarDefaultTitle : Theme.key_telegram_color_dialogsLogo));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        //arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItemIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_dialogButtonSelector));

        arrayList.addAll(SimpleThemeDescription.createThemeDescriptions(() -> {
        }, Theme.key_actionBarActionModeDefault, Theme.key_actionBarActionModeDefaultIcon));

        {
            RecyclerListView list = viewPage.listView;
            if (list != null) {

            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_scamDrawable, Theme.dialogs_fakeDrawable}, null, Theme.key_chats_draft));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable, Theme.dialogs_pinnedDrawable2, Theme.dialogs_reorderDrawable}, null, Theme.key_chats_pinnedIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable2Accent}, null, Theme.key_telegram_color_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messagePaint[1], null, null, Theme.key_chats_message_threeLines));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messagePaint[0], null, null, Theme.key_chats_message));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_draft));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_timePaintBold, null, null, Theme.key_chats_date_bold));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_timePaintBoldAccent, null, null, Theme.key_telegram_color_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_chats_onlineCircle));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{SavedChannelCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOX, new Class[]{SavedChannelCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SavedChannelCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(list, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));

            GraySectionCell.createThemeDescriptions(arrayList, list);

            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HashtagSearchCell.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
            }
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundSaved));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Red));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Orange));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Violet));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Green));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Cyan));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Blue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Pink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_background2Saved));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessage));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_attachMessage));

        {
            arrayList.add(new ThemeDescription(viewPage.listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite));

            arrayList.add(new ThemeDescription(viewPage.listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView1"}, null, null, null, Theme.key_chats_nameMessage_threeLines));
            arrayList.add(new ThemeDescription(viewPage.listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView2"}, null, null, null, Theme.key_chats_message));

            arrayList.add(new ThemeDescription(viewPage.listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(viewPage.listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
            arrayList.add(new ThemeDescription(viewPage.listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_telegram_color_text));

            arrayList.add(new ThemeDescription(viewPage.listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
            arrayList.add(new ThemeDescription(viewPage.listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

            arrayList.add(new ThemeDescription(viewPage.progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));
        }

        for (int a = 0; a < undoView.length; a++) {
            arrayList.add(new ThemeDescription(undoView[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"subinfoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info1", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info2", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc12", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc11", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc10", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc9", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc8", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc7", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc6", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc5", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc4", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc3", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc2", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc1", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Oval", Theme.key_undo_infoColor));
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackgroundGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextLink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLinkSelection));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_text_RedBold));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray3));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_text_RedRegular));
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
    final int slideAmplitudeDp = 40;
    boolean slideFragmentLite;
    boolean isSlideBackTransition;
    ValueAnimator slideBackTransitionAnimator;

    @Override
    public Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        if (backAnimation) {
            slideBackTransitionAnimator = ValueAnimator.ofFloat(slideFragmentProgress, 1f);
            return slideBackTransitionAnimator;
        }
        int duration = 150;
        if (getLayoutContainer() != null && getLayoutContainer().getMeasuredWidth() > 0) {
            duration = (int) Utilities.clamp(200.0f / getLayoutContainer().getMeasuredWidth() * distanceToMove, 200, 80);
        }
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
        if (SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_AVERAGE || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_SCALE)) {
            return;
        }
        if (sliding) {
            if (viewPage != null) {
                viewPage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                viewPage.setClipChildren(false);
                viewPage.setClipToPadding(false);
                viewPage.listView.setClipChildren(false);
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
                viewPage.listView.setClipChildren(true);
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
        if (SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW && !BuildVars.DEBUG_PRIVATE_VERSION) {
            return;
        }
        if (isSlideBackTransition && slideBackTransitionAnimator == null) {
            setSlideTransitionProgress(progress);
        }
    }

    private void setSlideTransitionProgress(float progress) {
        if (SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW && !BuildVars.DEBUG_PRIVATE_VERSION || slideFragmentProgress == progress) {
            return;
        }

        slideFragmentLite = SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_AVERAGE || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_SCALE);
        slideFragmentProgress = progress;
        if (fragmentView != null) {
            fragmentView.invalidate();
        }

    }

    @Override
    public INavigationLayout.BackButtonState getBackButtonState() {
        return INavigationLayout.BackButtonState.MENU;
    }

    @Override
    public boolean isLightStatusBar() {
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public List<FloatingDebugController.DebugItem> onGetDebugItems() {
        return Arrays.asList(
                new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugDialogsActivity)),
                new FloatingDebugController.DebugItem(LocaleController.getString(R.string.ClearLocalDatabase), () -> {
                    getMessagesStorage().clearLocalDatabase();
                    Toast.makeText(getContext(), LocaleController.getString(R.string.DebugClearLocalDatabaseSuccess), Toast.LENGTH_SHORT).show();
                }),
                new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugClearSendMessageAsPeers), () -> getMessagesController().clearSendAsPeers())
        );
    }

    @Override
    public boolean closeLastFragment() {
        return super.closeLastFragment();
    }

    @Override
    public boolean canBeginSlide() {
        return true;
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            return false;
        }

        final boolean isActionBarTouch = ev.getY() < actionBar.getMeasuredHeight();
        if (isActionBarTouch) {
            return true;
        }

        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    private int statusBarHeight;
    private int navigationBarHeight;
    private int imeInsetHeight;
    private int additionNavigationBarHeight;

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        windowInsetsStateHolder.setInsets(insets);

        statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        final int imeInsetHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        if (this.imeInsetHeight != imeInsetHeight) {
            this.imeInsetHeight = imeInsetHeight;
            fragmentView.requestLayout();
        }

        dialogsActivityStatusLayout.setPadding(0, statusBarHeight, 0, 0);

        ViewGroup.MarginLayoutParams lp;
        for (UndoView undoView : undoView) {
            if (undoView != null) {
                final int bottomMargin = navigationBarHeight + additionNavigationBarHeight;
                lp = (ViewGroup.MarginLayoutParams) undoView.getLayoutParams();
                if (lp != null && lp.bottomMargin != bottomMargin) {
                    lp.bottomMargin = bottomMargin;
                    undoView.setLayoutParams(lp);
                }
            }
        }

        return WindowInsetsCompat.CONSUMED;
    }


    /* Animations */

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SHADOW_VISIBLE) {
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        } else if (id == ANIMATOR_ID_ACTION_MODE_VISIBLE) {
            checkUi_menuItems();
        } else if (id == ANIMATOR_ID_FORWARD_BUTTON_VISIBLE) {
            checkUi_forwardCommentFieldVisible();
        }
    }

    private void checkUi_forwardCommentFieldVisible() {
        final float factor = animatorForwardButtonVisible.getFloatValue();
        final float s = lerp(0.2f, 1, factor);
    }

    private void checkUi_topPanelVisible() {
        //final float factor1 = 1f - animatorSearchVisible.getFloatValue();
        // final float factor2 = 1f - getRightSlidingProgress();
        final float factor = 1f; // factor1; // * factor2;

        if (topPanelLayout != null) {
            final float s = lerp(0.98f, 1f, factor);

            topPanelLayout.setAlpha(factor);
            topPanelLayout.setScaleX(s);
            topPanelLayout.setScaleY(s);
            topPanelLayout.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void checkUi_mainTabsVisible() {
        final boolean mainTabsVisible = (blurredView == null || blurredView.getBackground() == null || blurredView.getAlpha() < 0.01f || blurredView.getVisibility() == View.GONE);
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(mainTabsVisible);
        }
    }

    private void doRefresh() {
        if (savedChannelsMainAdapter == null || refreshAnimator != null) {
            return;
        }
        lastRefreshTime = System.currentTimeMillis();
        startRefreshAnimation();
        savedChannelsMainAdapter.refreshChats(() -> AndroidUtilities.runOnUIThread(this::stopRefreshAnimation));
    }

    private void startRefreshAnimation() {
        if (refreshItem == null || refreshItem.getIconView() == null) {
            return;
        }
        if (refreshAnimator != null) {
            return;
        }
        refreshAnimator = ObjectAnimator.ofFloat(refreshItem.getIconView(), "rotation", 0f, 360f);
        refreshAnimator.setDuration(700);
        refreshAnimator.setInterpolator(new LinearInterpolator());
        refreshAnimator.setRepeatCount(ValueAnimator.INFINITE);
        refreshAnimator.start();
    }

    private void stopRefreshAnimation() {
        if (refreshAnimator != null) {
            refreshAnimator.cancel();
            refreshAnimator = null;
        }
        if (refreshItem != null && refreshItem.getIconView() != null) {
            refreshItem.getIconView().setRotation(0f);
        }
    }

    private void checkUi_menuItems() {
        checkUi_itemBackButtonVisibility();
    }

    private void checkUi_itemBackButtonVisibility() {
        if (actionBar == null) {
            return;
        }
        if (hasMainTabs) {
            FragmentFloatingButton.setAnimatedVisibility(actionBar.getBackButton(), progressToActionMode);
        }
        if (!hasMainTabs && !actionBar.isActionModeShowed()) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
    }



    /* Blur */

    private ViewPositionWatcher viewPositionWatcher;

    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlassFrosted;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceGlass;
    private final @NonNull BlurredBackgroundSourceColor iBlur3SourceColor;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryFrostedLiquidGlass;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryBlur;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryLiquidGlass;
    private final @NonNull BlurredBackgroundDrawableViewFactory iBlur3FactoryFade;


    private IBlur3Capture iBlur3Capture;

    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
    private final RectF iBlur3PositionActionBar = new RectF();
    private final RectF iBlur3PositionMainTabs = new RectF(); {
        iBlur3Positions.add(iBlur3PositionActionBar);
        iBlur3Positions.add(iBlur3PositionMainTabs);
    }

    private void blur3_InvalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null || fragmentView == null || actionBar == null) {
            return;
        }

        final int additionalList = dp(48);
        final int mainTabBottom = fragmentView.getMeasuredHeight() - navigationBarHeight - dp(MAIN_TABS_MARGIN);
        final int mainTabTop = mainTabBottom - dp(MAIN_TABS_HEIGHT);

        final int actionBarHeight = actionBar.getMeasuredHeight()
            + dp(0)
            + (topPanelLayout != null && topPanelLayout.getVisibility() == View.VISIBLE ? topPanelLayout.getSumHeightOfAllVisibleChild() : 0)
            + ((int) scrollYOffset);

        final int actionBarHeightSearch = actionBar.getMeasuredHeight()
            + dp(30);

        iBlur3PositionActionBar.set(0, -additionalList, fragmentView.getMeasuredWidth(), lerp(actionBarHeight, actionBarHeightSearch, 0) + additionalList );

        boolean hasBottomBlur = false;
        if (hasMainTabs) {
            iBlur3PositionMainTabs.set(0, mainTabTop, fragmentView.getMeasuredWidth(), mainTabBottom);
            iBlur3PositionMainTabs.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -dp(48));

            hasBottomBlur = true;
        }

        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, hasBottomBlur ? 2 : 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());

        if (iBlur3SourceGlassFrosted != null) {
            iBlur3SourceGlassFrosted.setSize(fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
            iBlur3SourceGlassFrosted.updateDisplayListIfNeeded();
        }
        if (iBlur3SourceGlass != null) {
            iBlur3SourceGlass.setSize(fragmentView.getMeasuredWidth(), fragmentView.getMeasuredHeight());
            iBlur3SourceGlass.updateDisplayListIfNeeded();
        }
    }

    private int calculateListViewPaddingBottom() {
        return navigationBarHeight + additionNavigationBarHeight;
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return iBlur3SourceGlass;
    }

    @Override
    public void onParentScrollToTop() {
        scrollToTop(true);
    }

    public float getTopPanelAnimatedHeight() {
        return  getTopPanelAnimatedHeight(dp(14));
    }

    public float getTopPanelAnimatedHeight(int padding) {
        return topPanelLayout != null ? topPanelLayout.getAnimatedHeightWithPadding(padding) : 0;
    }

    public float getTopPanelVisibility() {
        return topPanelLayout != null ? topPanelLayout.getMetadata().getTotalVisibility() : 0;
    }

    private void checkInsets() {
        checkUi_chatListViewPaddingsBottom();
        blur3_InvalidateBlur();
    }

    private void checkUi_chatListViewPaddingsBottom() {
        if (viewPage != null) {
            final int listPaddingBottom = calculateListViewPaddingBottom();
            viewPage.listView.setPadding(0, viewPage.listView.topPadding, 0, listPaddingBottom);
        }
    }

    private void drawHeaderShadow(Canvas canvas, int sy) {
        if (parentLayout == null || actionBar == null /*|| !actionBar.getCastShadows()*/) {
            return;
        }

        final float headerShadowAlphaBase = animatorShadowVisible.getFloatValue();
        if (headerShadowAlphaBase == 0) {
            return;
        }

        float headerShadowAlpha = 0;
        int headerShadowY = -1;

        if (headerShadowY < sy) {
            headerShadowY = sy;
            headerShadowAlpha = 1f;
        }
        if (headerShadowAlpha > 0 && headerShadowAlphaBase > 0 && headerShadowY > 0 && parentLayout != null) {
            final int shadowAlpha = (int) (255 * headerShadowAlpha * headerShadowAlphaBase);
            parentLayout.drawHeaderShadow(canvas, shadowAlpha, headerShadowY);
        }
    }
}
