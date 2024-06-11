package org.telegram.ui.RemoveChatsAction;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.RemoveChatsAction.items.Item;
import org.telegram.ui.RemoveChatsAction.items.SearchItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RemoveChatsFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private EditTextBoldCursor editText;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private RemoveChatsAdapter adapter;
    private boolean ignoreScrollEvent;
    private int containerHeight;

    private final FakePasscode fakePasscode;
    private final RemoveChatsAction action;
    protected int accountNum;

    private boolean searchWas;
    private boolean searching;
    private GroupCreateSpan currentDeletingSpan;

    private NumberTextView selectedDialogsCountTextView;

    private int fieldY;

    private float progressToActionMode;
    private ValueAnimator actionBarColorAnimator;
    private BackDrawable backDrawable;
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem addItem;
    private ActionBarMenuItem editItem;

    private final static int delete = 100;
    private final static int add = 101;
    private final static int edit = 102;

    private static class ItemDecoration extends RecyclerView.ItemDecoration {

        private boolean single;

        public void setSingle(boolean value) {
            single = value;
        }

        @Override
        public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            int width = parent.getWidth();
            int top;
            int childCount = parent.getChildCount() - (single ? 0 : 1);
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                View nextChild = i < childCount - 1 ? parent.getChildAt(i + 1) : null;
                if (child instanceof GraySectionCell || nextChild instanceof GraySectionCell) {
                    continue;
                }
                top = child.getBottom();
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), top, width - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), top, Theme.dividerPaint);
            }
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.top = 1;
        }
    }

    private class SpansContainer extends ViewGroup {
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
                if (currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(13) + currentLineWidth;
                child.setTranslationX(x);
                child.setTranslationY(y);
                currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
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
            int currentHeight = allY + AndroidUtilities.dp(32 + 10);
            int fieldX = currentLineWidth + AndroidUtilities.dp(16);
            fieldY = y;
            containerHeight = currentHeight;
            editText.setTranslationX(fieldX);
            editText.setTranslationY(fieldY);
            setMeasuredDimension(width, containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }
    }

    public RemoveChatsFragment(FakePasscode fakePasscode, RemoveChatsAction action, int accountNum) {
        super();
        this.fakePasscode = fakePasscode;
        this.action = action;
        this.accountNum = accountNum;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.chatDidCreated);
        if (Utils.loadAllDialogs(accountNum)) {
            getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatDidCreated);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;
        currentDeletingSpan = null;

        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChatsToRemove", R.string.ChatsToRemove));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (adapter.isInSelectionMode()) {
                        adapter.clearSelection();
                        hideActionMode(true);
                    } else {
                        finishFragment();
                    }
                } else if (id == delete) {
                    adapter.deleteSelectedDialogs();
                    hideActionMode(true);
                    updateHint();
                } else if (id == add || id == edit) {
                    presentFragment(new RemoveChatSettingsFragment(fakePasscode, action, adapter.getSelectedItems(), accountNum));
                    adapter.clearSelection();
                    adapter.notifyDataSetChanged();
                    hideActionMode(true);
                    updateHint();
                }
            }
        });

        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), true);

        fragmentView = new ViewGroup(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                int maxSize;
                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = AndroidUtilities.dp(144);
                } else {
                    maxSize = AndroidUtilities.dp(56);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
                listView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                emptyView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
                emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView || child == emptyView) {
                    parentLayout.drawHeaderShadow(canvas, scrollView.getMeasuredHeight());
                }
                return result;
            }
        };
        ViewGroup frameLayout = (ViewGroup) fragmentView;

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
        editText.setHintText(LocaleController.getString("SearchForPeopleAndGroups", R.string.SearchForPeopleAndGroups));

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
        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty) {
                        updateHint();
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
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        emptyView.showProgress();
                    }
                    adapter.searchDialogs(editText.getText().toString());
                } else {
                    closeSearch();
                }
            }
        });

        emptyView = new EmptyTextProgressView(context);
        if (getContactsController().isLoadingContacts()) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        frameLayout.addView(emptyView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new RemoveChatsAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(new ItemDecoration());
        frameLayout.addView(listView);
        listView.setOnItemClickListener(this::onItemClick);
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            select(position);
            return true;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });
        updateHint();
        return fragmentView;
    }

    private void onItemClick(View view, int position) {
        if (view instanceof ChatRemoveCell) {
            ChatRemoveCell cell = (ChatRemoveCell) view;
            if (adapter.isInSelectionMode()) {
                select(position);
            } else {
                if (action.contains(cell.getItem().getId())) {
                    showRemoveDialog(() -> {
                        removeEntry(cell.getItem().getId());
                        if (searching || searchWas) {
                            AndroidUtilities.showKeyboard(editText);
                        } else {
                            cell.setChecked(false, true);
                        }
                    });
                } else {
                    Set<Item> items = adapter.getItemsToEdit(position); // editText.setText(null); set searching = false. Therefore why ids must be obtained before
                    if (editText.length() > 0) {
                        editText.setText(null);
                    }
                    presentFragment(new RemoveChatSettingsFragment(fakePasscode, action, items, accountNum));
                }
            }
        }
    }

    private void showRemoveDialog(Runnable onAccepted) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        String buttonText;
        builder.setMessage(LocaleController.getString(R.string.RemoveDialogFromListAlert));
        builder.setTitle(LocaleController.getString(R.string.RemoveDialogFromListTitle));
        buttonText = LocaleController.getString(R.string.ClearSearchRemove);
        builder.setPositiveButton(buttonText, (dialogInterface, i) -> onAccepted.run());
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_color_red));
        }
    }

    private void removeEntry(long dialogId) {
        action.remove(dialogId);
        SharedConfig.saveConfig();
        updateHint();
        if (editText.length() > 0) {
            editText.setText(null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.requestFocus();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateHint();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (emptyView != null) {
                emptyView.showTextView();
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (listView != null) {
                int mask = (Integer) args[0];
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    updateAllCells();
                }
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.dialogsNeedReload) {
            if (Utils.getAllDialogs(currentAccount).size() > 10_000 || !Utils.loadAllDialogs(accountNum)) {
                if (emptyView != null) {
                    emptyView.showTextView();
                }
                if (adapter != null) {
                    adapter.fillItems();
                    adapter.notifyDataSetChanged();
                }
                getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
            }
        }
    }

    private void updateAllCells() {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatRemoveCell) {
                ((ChatRemoveCell) child).update();
            }
        }
    }

    @Override
    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    @Keep
    public void setContainerHeight(int value) {
        containerHeight = value;
        if (spansContainer != null) {
            spansContainer.requestLayout();
        }
    }

    @Keep
    public int getContainerHeight() {
        return containerHeight;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatRemoveCell) {
                ChatRemoveCell cell = (ChatRemoveCell) child;
                long id = cell.getItem().getId();
                if (id != 0) {
                    cell.setChecked(action.contains(id), true);
                    cell.setCheckBoxEnabled(true);
                }
            }
        }
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
    }

    private void updateHint() {
        actionBar.setSubtitle(LocaleController.formatPluralString("Chats", action.getChatEntriesToRemove().size()));
    }

    private void select(int position) {
        if (adapter == null) {
            return;
        }
        adapter.select(position);
        showOrUpdateActionMode();
    }

    private void showOrUpdateActionMode() {
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            if (!adapter.isInSelectionMode()) {
                hideActionMode(true);
                return;
            }

            updateMenuItemsVisibility();
            updateAnimated = true;
        } else {
            createActionMode(null);
            updateMenuItemsVisibility();
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            actionBar.showActionMode();

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

            animateActionBarColor(true);
            if (backDrawable != null) {
                backDrawable.setRotation(1, true);
            }
        }
        selectedDialogsCountTextView.setNumber(adapter.getSelectedDialogsCount(), updateAnimated);
    }

    private void updateMenuItemsVisibility() {
        boolean isEdit = adapter.isSelectedConfiguredDialog();
        addItem.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        editItem.setVisibility(isEdit ? View.VISIBLE : View.GONE);
        deleteItem.setVisibility(isEdit ? View.VISIBLE : View.GONE);
    }

    private void animateActionBarColor(boolean forward) {
        if (actionBarColorAnimator != null) {
            actionBarColorAnimator.cancel();
        }
        if (forward) {
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 1f);
        } else {
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 0f);
        }
        actionBarColorAnimator.addUpdateListener(valueAnimator -> {
            progressToActionMode = (float) valueAnimator.getAnimatedValue();
            actionBar.setBackgroundColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault), Theme.getColor(Theme.key_windowBackgroundWhite), progressToActionMode));
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

    private void createActionMode(String tag) {
        if (actionBar.actionModeIsExist(tag)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, tag);
        actionMode.setBackground(null);

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));
        addItem = actionMode.addItemWithWidth(add, R.drawable.msg_add, AndroidUtilities.dp(54), LocaleController.getString("Add", R.string.Add));
        editItem = actionMode.addItemWithWidth(edit, R.drawable.msg_edit, AndroidUtilities.dp(54), LocaleController.getString("Edit", R.string.Edit));

        actionModeViews.add(deleteItem);
        actionModeViews.add(addItem);
        actionModeViews.add(editItem);
        updateMenuItemsVisibility();
    }

    private void hideActionMode(boolean animateCheck) {
        actionBar.hideActionMode();
        adapter.selectedItems.clear();
        if (backDrawable != null) {
            backDrawable.setRotation(0, true);
        }
        animateActionBarColor(false);
        adapter.notifyDataSetChanged();
    }

    private class RemoveChatsAdapter extends RecyclerListView.FastScrollAdapter {

        private final Context context;
        private List<SearchItem> searchResult = new ArrayList<>();
        private final Set<Item> selectedItems = new HashSet<>();
        private Runnable searchRunnable;
        private boolean searching;
        private final List<Item> items = new ArrayList<>();

        public RemoveChatsAdapter(Context ctx) {
            context = ctx;
            fillItems();
        }

        public void fillItems() {
            items.clear();
            for (Long id: getChatIds()) {
                Item item = Item.tryCreateItemById(accountNum, action, id);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        private List<Long> getChatIds() {
            LongSparseIntArray blockedPeers = getMessagesController().getUnfilteredBlockedPeers();
            return concatStreams(
                    action.getIds().stream(),
                    getMessagesController().getAllDialogs().stream().map(d -> d.id),
                    IntStream.range(0, blockedPeers.size()).boxed().map(blockedPeers::keyAt),
                    Stream.of(getUserConfig().clientUserId)
            ).distinct().collect(Collectors.toList());
        }

        @SafeVarargs
        private final <T> Stream<T> concatStreams(Stream<T> stream, Stream<T>... otherStreams) {
            Stream<T> resultStream = stream;
            for (Stream<T> otherStream : otherStreams) {
                resultStream = Stream.concat(resultStream, otherStream);
            }
            return resultStream;
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
            return null;
        }

        @Override
        public int getItemCount() {
            if (searching) {
                return searchResult.size();
            } else {
                return items.size();
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                default:
                case 1:
                    view = new ChatRemoveCell(context, accountNum);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    if (position < 0) {
                        return;
                    }
                    ChatRemoveCell cell = (ChatRemoveCell) holder.itemView;
                    Item item = getItem(position);
                    cell.setItemSelected(selectedItems.contains(item.getId()));
                    cell.setOnSettingsClick(this::editChatToRemove);
                    cell.setItem(item);
                    cell.setChecked(action.contains(item.getId()), false);
                    cell.setCheckBoxEnabled(true);
                    break;
                }
            }
        }

        private Item getItem(int position) {
            return searching
                    ? searchResult.get(position)
                    : items.get(position);
        }

        @Override
        public int getItemViewType(int position) {
            return 1;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = (int) (getItemCount() * progress);
            position[1] = 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChatRemoveCell) {
                ((ChatRemoveCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null) {
                updateSearchResults(Collections.emptyList());
                return;
            }
            Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                List<SearchItem> resultItems = new ArrayList<>();
                for (Item item : items) {
                    for (String queryVariant : makeQueryVariants(query)) {
                        SearchItem searchItem = item.search(queryVariant);
                        if (searchItem != null) {
                            resultItems.add(searchItem);
                            break;
                        }
                    }
                }
                updateSearchResults(resultItems);
            }, 300);
        }

        private List<String> makeQueryVariants(String query) {
            String rawQuery = query.trim();
            String translitQuery = LocaleController.getInstance().getTranslitString(rawQuery);
            return Stream.of(rawQuery, translitQuery)
                    .filter(q -> !q.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }

        private void updateSearchResults(final List<SearchItem> users) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchRunnable = null;
                searchResult = users;
                emptyView.showTextView();
                notifyDataSetChanged();
            });
        }

        private void editChatToRemove(Item item) {
            if (!action.contains(item.getId())) {
                return;
            }
            selectedItems.clear();
            if (listView != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            hideActionMode(true);
            updateHint();
            presentFragment(new RemoveChatSettingsFragment(fakePasscode, action, Collections.singleton(item), accountNum));
        }

        public void select(int position) {
            Item item = getItem(position);
            if (selectedItems.contains(item)) {
                selectedItems.remove(item);
                notifyItemChanged(position);
            } else {
                selectedItems.add(item);
                notifyItemChanged(position);
                items.stream()
                        .filter(i -> i.shouldBeEditedToo(item))
                        .forEach(selectedItems::add);
                for (int otherPosition = 0; otherPosition < getItemCount(); otherPosition++) {
                    if (getItem(otherPosition).shouldBeEditedToo(item)) {
                        notifyItemChanged(otherPosition);
                    }
                }
            }
        }

        public boolean isInSelectionMode() {
            return !selectedItems.isEmpty();
        }

        public void clearSelection() {
            selectedItems.clear();
        }

        public int getSelectedDialogsCount() {
            return selectedItems.size();
        }

        public boolean isSelectedConfiguredDialog() {
            return selectedItems.stream().anyMatch(i -> action.contains(i.getId()));
        }

        public void deleteSelectedDialogs() {
            for (Item item : selectedItems) {
                action.remove(item.getId());
            }
        }

        public Set<Item> getSelectedItems() {
            return selectedItems;
        }

        public Set<Item> getItemsToEdit(int position) {
            Item targetItem = getItem(position);
            return items.stream()
                    .filter(item -> item.getId() == targetItem.getId() || item.shouldBeEditedToo(targetItem))
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::updateAllCells;

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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ChatRemoveCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ChatRemoveCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatRemoveCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
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

        return themeDescriptions;
    }
}
