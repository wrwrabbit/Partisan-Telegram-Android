package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.annotation.Keep;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupConstants;
import org.telegram.messenger.partisan.secretgroups.SecretGroupStarter;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.GroupCreateDividerItemDecoration;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SecretGroupCreateActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener {

    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private EditTextBoldCursor editText;
    private RecyclerListView listView;
    private StickerEmptyView emptyView;
    private GroupCreateAdapter adapter;
    private GroupCreateDividerItemDecoration itemDecoration;
    private AnimatorSet currentDoneButtonAnimation;
    private ImageView floatingButton;
    private boolean doneButtonVisible;
    private boolean ignoreScrollEvent;

    private int measuredContainerHeight;
    private int containerHeight;

    private final int maxCount = EncryptedGroupConstants.MAX_MEMBER_COUNT - 1;
    private boolean searchWas;
    private boolean searching;
    private final LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();
    private final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;

    private AlertDialog creationProgressDialog;

    private int fieldY;

    private AnimatorSet currentAnimation;
    int maxSize;

    private final static int done_button = 1;

    private ArrayList<Long> toSelectIds;
    private void select(ArrayList<Long> ids) {
        if (spansContainer == null) {
            toSelectIds = ids;
            return;
        }
        for (long id : ids) {
            TLObject obj;
            if (id < 0) {
                obj = getMessagesController().getChat(-id);
            } else {
                obj = getMessagesController().getUser(id);
            }
            if (obj == null) continue;
            GroupCreateSpan span = new GroupCreateSpan(getContext(), obj, currentAccount);
            spansContainer.addSpan(span);
            span.setOnClickListener(this);
        }
        spansContainer.endAnimation();
        AndroidUtilities.updateVisibleRows(listView);
    }

    private class SpansContainer extends ViewGroup {

        private boolean animationStarted;
        private ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private final ArrayList<View> removingSpans = new ArrayList<>();
        private int animationIndex = -1;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - AndroidUtilities.dp(26);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(10);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(10);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                boolean isRemoving = removingSpans.contains(child);
                if (!isRemoving && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (isRemoving) {
                        child.setTranslationX(AndroidUtilities.dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (!removingSpans.isEmpty()) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, "translationX", x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, "translationY", y));
                        }
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                    }
                }
                if (!isRemoving) {
                    currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = AndroidUtilities.dp(530 - 26 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(26 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += AndroidUtilities.dp(32 + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += AndroidUtilities.dp(32 + 8);
            }
            editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
            if (!animationStarted) {
                int currentHeight = allY + AndroidUtilities.dp(32 + 10);
                int fieldX = currentLineWidth + AndroidUtilities.dp(16);
                fieldY = y;
                if (currentAnimation != null) {
                    int resultHeight = y + AndroidUtilities.dp(32 + 10);
                    if (containerHeight != resultHeight) {
                        animators.add(ObjectAnimator.ofInt(SecretGroupCreateActivity.this, "containerHeight", resultHeight));
                    }
                    measuredContainerHeight = Math.max(containerHeight, resultHeight);
                    if (editText.getTranslationX() != fieldX) {
                        animators.add(ObjectAnimator.ofFloat(editText, "translationX", fieldX));
                    }
                    if (editText.getTranslationY() != fieldY) {
                        animators.add(ObjectAnimator.ofFloat(editText, "translationY", fieldY));
                    }
                    editText.setAllowDrawCursor(false);
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            getNotificationCenter().onAnimationFinish(animationIndex);
                            requestLayout();
                        }
                    });
                    animationIndex = getNotificationCenter().setAnimationInProgress(animationIndex, null);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    measuredContainerHeight = containerHeight = currentHeight;
                    editText.setTranslationX(fieldX);
                    editText.setTranslationY(fieldY);
                }
            } else if (currentAnimation != null) {
                if (!ignoreScrollEvent && removingSpans.isEmpty()) {
                    editText.bringPointIntoView(editText.getSelectionStart());
                }
            }
            setMeasuredDimension(width, measuredContainerHeight);
            listView.setTranslationY(0);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span) {
            allSpans.add(span);
            if (!span.isFlag) {
                selectedContacts.put(span.getUid(), span);
            }

            editText.setHintVisible(false, TextUtils.isEmpty(editText.getText()));
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    addingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                    editText.setAllowDrawCursor(true);
                }
            });
            currentAnimation.setDuration(150);
            addingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
            addView(span);
        }

        public void endAnimation() {
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            if (!span.isFlag) {
                selectedContacts.remove(span.getUid());
            }
            allSpans.remove(span);
            span.setOnClickListener(null);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpans.clear();
                    currentAnimation = null;
                    animationStarted = false;
                    editText.setAllowDrawCursor(true);
                    if (allSpans.isEmpty()) {
                        editText.setHintVisible(true, true);
                    }
                }
            });
            currentAnimation.setDuration(150);
            removingSpans.clear();
            removingSpans.add(span);
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }

        public void removeAllSpans(boolean animated) {
            ignoreScrollEvent = true;

            ArrayList<GroupCreateSpan> spans = new ArrayList<>(allSpans);
            allSpans.clear();

            removingSpans.clear();
            removingSpans.addAll(spans);

            for (int i = 0; i < spans.size(); ++i) {
                spans.get(i).setOnClickListener(null);
            }

            endAnimation();
            if (animated) {
                animationStarted = false;
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        for (int i = 0; i < spans.size(); ++i) {
                            removeView(spans.get(i));
                        }
                        removingSpans.clear();
                        currentAnimation = null;
                        animationStarted = false;
                        editText.setAllowDrawCursor(true);
                        if (allSpans.isEmpty()) {
                            editText.setHintVisible(true, true);
                        }
                    }
                });
                animators.clear();
                for (int i = 0; i < spans.size(); ++i) {
                    GroupCreateSpan span = spans.get(i);
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                }
            } else {
                for (int i = 0; i < spans.size(); ++i) {
                    removeView(spans.get(i));
                }
                removingSpans.clear();
                currentAnimation = null;
                animationStarted = false;
                editText.setAllowDrawCursor(true);
                if (allSpans.isEmpty()) {
                    editText.setHintVisible(true, true);
                }
            }
            requestLayout();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.chatDidCreated);

        getUserConfig().loadGlobalTTl();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatDidCreated);
    }

    @Override
    public void onClick(View v) {
        GroupCreateSpan span = (GroupCreateSpan) v;
        if (span.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(span);
            selectedCountUpdated();
            checkVisibleRows();
        } else {
            if (currentDeletingSpan != null) {
                currentDeletingSpan.cancelDeleteAnimation();
            }
            currentDeletingSpan = span;
            span.startDeleteAnimation();
        }
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;
        allSpans.clear();
        selectedContacts.clear();
        currentDeletingSpan = null;
        doneButtonVisible = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NewEncryptedGroup));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    onDonePressed();
                }
            }
        });

        fragmentView = new ViewGroup(context) {

            private VerticalPositionAutoAnimator verticalPositionAutoAnimator;

            @Override
            public void onViewAdded(View child) {
                if (child == floatingButton && verticalPositionAutoAnimator == null) {
                    verticalPositionAutoAnimator = VerticalPositionAutoAnimator.attach(child);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (verticalPositionAutoAnimator != null) {
                    verticalPositionAutoAnimator.ignoreNextLayout();
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = AndroidUtilities.dp(144);
                } else {
                    maxSize = AndroidUtilities.dp(56);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
                listView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                emptyView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                if (floatingButton != null) {
                    int w = AndroidUtilities.dp(Build.VERSION.SDK_INT >= 21 ? 56 : 60);
                    floatingButton.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY));
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
                emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());

                if (floatingButton != null) {
                    int l = LocaleController.isRTL ? AndroidUtilities.dp(14) : (right - left) - AndroidUtilities.dp(14) - floatingButton.getMeasuredWidth();
                    int t = bottom - top - AndroidUtilities.dp(14) - floatingButton.getMeasuredHeight();
                    floatingButton.layout(l, t, l + floatingButton.getMeasuredWidth(), t + floatingButton.getMeasuredHeight());
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                parentLayout.drawHeaderShadow(canvas, Math.min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight));
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == listView) {
                    canvas.save();
                    canvas.clipRect(child.getLeft(), Math.min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight), child.getRight(), child.getBottom());
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return result;
                } else if (child == scrollView) {
                    canvas.save();
                    canvas.clipRect(child.getLeft(), child.getTop(), child.getRight(), Math.min(maxSize, measuredContainerHeight + containerHeight - measuredContainerHeight));
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return result;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
        ViewGroup frameLayout = (ViewGroup) fragmentView;
        frameLayout.setFocusableInTouchMode(true);
        frameLayout.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (ignoreScrollEvent) {
                    ignoreScrollEvent = false;
                    return false;
                }
                rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
                rectangle.top += fieldY + AndroidUtilities.dp(20);
                rectangle.bottom += fieldY + AndroidUtilities.dp(50);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        scrollView.setClipChildren(false);
        frameLayout.setClipChildren(false);
        scrollView.setVerticalScrollBarEnabled(false);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(scrollView);

        spansContainer = new SpansContainer(context);
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        spansContainer.setOnClickListener(v -> {
            editText.clearFocus();
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        editText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                    currentDeletingSpan = null;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(Theme.getColor(Theme.key_groupcreate_cursor));
        editText.setCursorWidth(1.5f);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setTextIsSelectable(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        spansContainer.addView(editText);
        updateEditTextHint();
        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
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
        editText.setOnEditorActionListener((v, actionId, event) -> actionId == EditorInfo.IME_ACTION_DONE && onDonePressed());
        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()){
                        spansContainer.removeSpan(allSpans.get(allSpans.size() - 1));
                        selectedCountUpdated();
                        checkVisibleRows();
                        return true;
                    }
                }
                return false;
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editText.length() != 0) {
                    if (!adapter.searching) {
                        searching = true;
                        searchWas = true;
                        adapter.setSearching(true);
                        itemDecoration.setSearching(true);
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    adapter.searchDialogs(editText.getText().toString());
                    emptyView.showProgress(true, false);
                } else {
                    closeSearch();
                }
            }
        });

        if (toSelectIds != null) {
            select(toSelectIds);
        }

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView);
        emptyView.showProgress(true, false);
        emptyView.title.setText(LocaleController.getString(R.string.NoResult));

        frameLayout.addView(emptyView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(itemDecoration = new GroupCreateDividerItemDecoration());
        frameLayout.addView(listView);
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                Object object = cell.getObject();
                long id;
                if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    return;
                }
                if (selectedContacts.indexOfKey(id) >= 0) {
                    GroupCreateSpan span = selectedContacts.get(id);
                    spansContainer.removeSpan(span);
                } else {
                    if (maxCount != 0 && selectedContacts.size() == maxCount) {
                        return;
                    }
                    if (selectedContacts.size() == getMessagesController().maxGroupCount) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString(R.string.AppName));
                        builder.setMessage(LocaleController.getString(R.string.SoftUserLimitAlert));
                        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                        showDialog(builder.create());
                        return;
                    }
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        getMessagesController().putUser(user, !searching);
                    } else if (object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        getMessagesController().putChat(chat, !searching);
                    }
                    GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object, currentAccount);
                    spansContainer.addSpan(span);
                    span.setOnClickListener(SecretGroupCreateActivity.this);
                }
                selectedCountUpdated();
                if (searching || searchWas) {
                    AndroidUtilities.showKeyboard(editText);
                } else {
                    checkVisibleRows();
                }
                if (editText.length() > 0) {
                    editText.setText(null);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    editText.hideActionMode();
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setArrowRotation(180);
        floatingButton.setImageDrawable(backDrawable);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        frameLayout.addView(floatingButton);
        floatingButton.setOnClickListener(v -> onDonePressed());
        if (!doneButtonVisible) {
            floatingButton.setVisibility(View.INVISIBLE);
            floatingButton.setScaleX(0.0f);
            floatingButton.setScaleY(0.0f);
            floatingButton.setAlpha(0.0f);
        }
        floatingButton.setContentDescription(LocaleController.getString(R.string.Next));

        selectedCountUpdated();
        return fragmentView;
    }

    private void updateEditTextHint() {
        if (editText == null) {
            return;
        }
        if (adapter != null && adapter.noContactsStubRow == 0) {
            editText.setHintText(LocaleController.getString(R.string.SearchForPeople));
        } else {
            editText.setHintText(LocaleController.getString(R.string.SendMessageTo));
        }
    }

    private void showItemsAnimated(int from) {
        if (isPaused) {
            return;
        }
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    if (listView.getChildAdapterPosition(child) < from) {
                        continue;
                    }
                    child.setAlpha(0);
                    int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                    int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                    a.setStartDelay(delay);
                    a.setDuration(200);
                    animatorSet.playTogether(a);
                }
                animatorSet.start();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (listView != null) {
                int mask = (Integer) args[0];
                int count = listView.getChildCount();
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof GroupCreateUserCell) {
                            ((GroupCreateUserCell) child).update(mask);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            removeSelfFromStack();
        }
    }

    @Keep
    public void setContainerHeight(int value) {
        int dy = containerHeight - value;
        containerHeight = value;
        int measuredH = Math.min(maxSize, measuredContainerHeight);
        int currentH = Math.min(maxSize, containerHeight);
        scrollView.scrollTo(0, Math.max(0, scrollView.getScrollY() - dy));
        listView.setTranslationY(currentH - measuredH);
        fragmentView.invalidate();
    }

    @Keep
    public int getContainerHeight() {
        return containerHeight;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) child;
                Object object = cell.getObject();
                long id;
                if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = 0;
                }
                if (id != 0) {
                    cell.setChecked(selectedContacts.indexOfKey(id) >= 0, true);
                    cell.setCheckBoxEnabled(true);
                }
            } else if (child instanceof GraySectionCell) {
                int position = listView.getChildAdapterPosition(child);
                if (position == adapter.firstSectionRow) {
                    GraySectionCell cell = (GraySectionCell) child;
                    cell.setRightText(!selectedContacts.isEmpty() ? LocaleController.getString(R.string.DeselectAll) : "", true, v -> {
                        selectedContacts.clear();
                        spansContainer.removeAllSpans(true);
                        checkVisibleRows();
                        updateEditTextHint();
                    });
                }
            }
        }
    }

    private boolean onDonePressed() {
        if (!doneButtonVisible) {
            return false;
        }
        ArrayList<Long> result = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            result.add(selectedContacts.keyAt(a));
        }
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.CREATE;
        template.title = LocaleController.getString(R.string.GroupName);
        template.addEditTemplate("", LocaleController.getString(R.string.EnterGroupNamePlaceholder), true);
        template.positiveListener = views -> {
            String chatName = ((EditTextCaption)views.get(0)).getText().toString();
            creationProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            showDialog(creationProgressDialog);

            List<TLRPC.User> users = result.stream()
                    .map(id -> getMessagesController().getUser(id))
                    .collect(Collectors.toList());

            SecretGroupStarter.startSecretGroup(currentAccount, getContext(), users, chatName, group -> {
                if (!group.isPresent()) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (creationProgressDialog != null) {
                        creationProgressDialog.dismiss();
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats);

                    Bundle args = new Bundle();
                    args.putInt("enc_group_id", group.get().getInternalId());
                    args.putBoolean("just_created_chat", true);
                    presentFragment(new ChatActivity(args), true);
                });
            });
        };
        showDialog(FakePasscodeDialogBuilder.build(getContext(), template));
        return true;
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        itemDecoration.setSearching(false);
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        showItemsAnimated(0);
    }

    private void selectedCountUpdated() {
        updateHint();
        checkDoneButton();
    }

    private void updateHint() {
        if (selectedContacts.size() == 0) {
            actionBar.setSubtitle(LocaleController.formatString("MembersCountZero", R.string.MembersCountZero, LocaleController.formatPluralString("Members", maxCount)));
        } else {
            String str = LocaleController.getPluralString("MembersCountSelected", selectedContacts.size());
            actionBar.setSubtitle(String.format(str, selectedContacts.size(), maxCount));
        }
    }

    private void checkDoneButton() {
        if (doneButtonVisible && allSpans.isEmpty()) {
            if (currentDoneButtonAnimation != null) {
                currentDoneButtonAnimation.cancel();
            }
            currentDoneButtonAnimation = new AnimatorSet();
            currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.0f),
                    ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.0f),
                    ObjectAnimator.ofFloat(floatingButton, View.ALPHA, 0.0f));
            currentDoneButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    floatingButton.setVisibility(View.INVISIBLE);
                }
            });
            currentDoneButtonAnimation.setDuration(180);
            currentDoneButtonAnimation.start();
            doneButtonVisible = false;
        } else if (!doneButtonVisible && !allSpans.isEmpty()) {
            if (currentDoneButtonAnimation != null) {
                currentDoneButtonAnimation.cancel();
            }
            currentDoneButtonAnimation = new AnimatorSet();
            floatingButton.setVisibility(View.VISIBLE);
            currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(floatingButton, View.ALPHA, 1.0f));
            currentDoneButtonAnimation.setDuration(180);
            currentDoneButtonAnimation.start();
            doneButtonVisible = true;
        }
    }

    public class GroupCreateAdapter extends RecyclerListView.FastScrollAdapter {

        private final Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private final SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private boolean searching;
        private final ArrayList<TLObject> contacts = new ArrayList<>();
        private int userTypesHeaderRow;
        private int firstSectionRow;
        private int usersStartRow;
        private int noContactsStubRow;
        private int currentItemsCount;

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEditTextHint();
        }

        public GroupCreateAdapter(Context ctx) {
            context = ctx;

            ArrayList<TLRPC.TL_contact> arrayList = (ArrayList<TLRPC.TL_contact>) FakePasscodeUtils.filterContacts(getContactsController().contacts, currentAccount);
            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.User user = getMessagesController().getUser(arrayList.get(a).user_id);
                if (user == null || user.self || user.deleted) {
                    continue;
                }
                contacts.add(user);
            }

            searchAdapterHelper = new SearchAdapterHelper(false);
            searchAdapterHelper.setDelegate((searchId) -> {
                showItemsAnimated(currentItemsCount);
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
                notifyDataSetChanged();
            });
        }

        public void setSearching(boolean value) {
            if (searching == value) {
                return;
            }
            searching = value;
            notifyDataSetChanged();
        }

        @Override
        public String getLetter(int position) {
            if (searching || position < usersStartRow || position >= contacts.size() + usersStartRow) {
                return null;
            }
            TLObject object = contacts.get(position - usersStartRow);
            String firstName;
            String lastName;
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                firstName = user.first_name;
                lastName = user.last_name;
            } else {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                firstName = getUserConfig().getChatTitleOverride(chat);
                lastName = "";
            }
            if (LocaleController.nameDisplayOrder == 1) {
                if (!TextUtils.isEmpty(firstName)) {
                    return firstName.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(lastName)) {
                    return lastName.substring(0, 1).toUpperCase();
                }
            } else {
                if (!TextUtils.isEmpty(lastName)) {
                    return lastName.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(firstName)) {
                    return firstName.substring(0, 1).toUpperCase();
                }
            }
            return "";
        }

        @Override
        public int getItemCount() {
            int count;
            noContactsStubRow = -1;
            userTypesHeaderRow = -1;
            firstSectionRow = -1;
            if (searching) {
                count = searchResult.size();
                int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                int globalCount = searchAdapterHelper.getGlobalSearch().size();
                count += localServerCount;
                if (globalCount != 0) {
                    count += globalCount + 1;
                }
                currentItemsCount = count;
                return count;
            } else {
                count = 0;
                firstSectionRow = count;
                usersStartRow = count;
                count += contacts.size();
                if (count == 0) {
                    noContactsStubRow = 0;
                    count++;
                }
            }
            currentItemsCount = count;
            return count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    view = new GraySectionCell(context);
                    break;
                case 1:
                    view = new GroupCreateUserCell(context, 1, 0, false);
                    break;
                case 3:
                    StickerEmptyView stickerEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_NO_CONTACTS) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            stickerView.getImageReceiver().startAnimation();
                        }
                    };
                    stickerEmptyView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    stickerEmptyView.subtitle.setVisibility(View.GONE);
                    stickerEmptyView.title.setText(LocaleController.getString(R.string.NoContacts));
                    stickerEmptyView.setAnimateLayoutChange(true);
                    view = stickerEmptyView;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    if (searching) {
                        cell.setText(LocaleController.getString(R.string.GlobalSearch));
                    } else if (position == userTypesHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.PrivacyUserTypes));
                    }
                    if (position == firstSectionRow) {
                        cell.setRightText(!selectedContacts.isEmpty() ? LocaleController.getString(R.string.DeselectAll) : "", true, v -> {
                            selectedContacts.clear();
                            spansContainer.removeAllSpans(true);
                            checkVisibleRows();
                            updateEditTextHint();
                        });
                    }
                    break;
                }
                case 1: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLObject object;
                    CharSequence username = null;
                    CharSequence name = null;
                    if (searching) {
                        int localCount = searchResult.size();
                        int globalCount = searchAdapterHelper.getGlobalSearch().size();
                        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

                        if (position >= 0 && position < localCount) {
                            object = (TLObject) searchResult.get(position);
                        } else if (position >= localCount && position < localServerCount + localCount) {
                            object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                        } else if (position > localCount + localServerCount && position <= globalCount + localCount + localServerCount) {
                            object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount - 1);
                        } else {
                            object = null;
                        }
                        if (object != null) {
                            String objectUserName;
                            if (object instanceof TLRPC.User) {
                                objectUserName = ((TLRPC.User) object).username;
                            } else if (object instanceof TLRPC.Chat) {
                                objectUserName = ChatObject.getPublicUsername((TLRPC.Chat) object);
                            } else {
                                return;
                            }
                            if (position < localCount) {
                                name = searchResultNames.get(position);
                                if (name != null && !TextUtils.isEmpty(objectUserName)) {
                                    if (name.toString().startsWith("@" + objectUserName)) {
                                        username = name;
                                        name = null;
                                    }
                                }
                            } else if (position > localCount && !TextUtils.isEmpty(objectUserName)) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    int index;
                                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                    spannableStringBuilder.append("@");
                                    spannableStringBuilder.append(objectUserName);
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName)) != -1) {
                                        int len = foundUserName.length();
                                        if (index == 0) {
                                            len++;
                                        } else {
                                            index++;
                                        }
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = objectUserName;
                                }
                            }
                        }
                    } else {
                        object = contacts.get(position - usersStartRow);
                    }
                    cell.setObject(object, name, username);
                    long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = 0;
                    }
                    if (id != 0) {
                        cell.setChecked(selectedContacts.indexOfKey(id) >= 0, false);
                        cell.setCheckBoxEnabled(true);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (searching) {
                if (position == searchResult.size() + searchAdapterHelper.getLocalServerSearch().size()) {
                    return 0;
                }
                return 1;
            } else {
                if (position == userTypesHeaderRow) {
                    return 0;
                }
                if (noContactsStubRow == position) {
                    return 3;
                }
                return 1;
            }
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = (int) (getItemCount() * progress);
            position[1] = 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 0;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }

            searchResult.clear();
            searchResultNames.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, false, false, false, false, 0, false, 0, 0);
            notifyDataSetChanged();

            if (!TextUtils.isEmpty(query)){
                Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                    searchAdapterHelper.queryServerSearch(query, true, false, true, false, false, 0, false, 0, 0);
                    Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<Object> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contacts.size(); a++) {
                            TLObject object = contacts.get(a);

                            String name;
                            String username;

                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = UserObject.getPublicUsername(user);
                            } else if (object instanceof TLRPC.Chat) {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                name = getUserConfig().getChatTitleOverride(chat);
                                username = ChatObject.getPublicUsername(chat);
                            } else {
                                continue;
                            }
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        if (object instanceof TLRPC.User) {
                                            TLRPC.User user = (TLRPC.User) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else if (object instanceof TLRPC.Chat) {
                                            TLRPC.Chat chat = (TLRPC.Chat) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(getUserConfig().getChatTitleOverride(chat), null, q));
                                        }
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                    }
                                    resultArray.add(object);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultArrayNames);
                    });
                }), 300);
            }
        }

        private void updateSearchResults(final ArrayList<Object> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchRunnable = null;
                searchResult = users;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                showItemsAnimated(currentItemsCount);
                notifyDataSetChanged();
                if (searching && !searchAdapterHelper.isSearchInProgress() && getItemCount() == 0) {
                    emptyView.showProgress(false, true);
                }
            });
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GroupCreateSectionCell.class}, null, null, null, Theme.key_graySection));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateSectionCell.class}, new String[]{"drawable"}, null, null, null, Theme.key_groupcreate_sectionShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanBackground));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanText));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanDelete));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_avatar_backgroundBlue));

        themeDescriptions.add(new ThemeDescription(emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        return themeDescriptions;
    }
}