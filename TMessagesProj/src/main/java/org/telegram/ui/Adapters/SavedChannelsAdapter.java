/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_chatlists;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Cells.SavedChannelCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.DialogsRequestedEmptyCell;
import org.telegram.ui.Cells.RequestPeerRequirementsCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.SavedChannelsActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SavedChannelsAdapter extends RecyclerListView.SelectionAdapter implements SavedChannelCell.SavedChannelCellDelegate {
    public final static int VIEW_TYPE_DIALOG = 0,
            VIEW_TYPE_FLICKER = 1,
            VIEW_TYPE_EMPTY = 5,
            VIEW_TYPE_LAST_EMPTY = 10,
            VIEW_TYPE_REQUIREMENTS = 15,
            VIEW_TYPE_REQUIRED_EMPTY = 16;

    private Context mContext;
    private int dialogsCount;
    private long openedDialogId;
    private int currentCount;
    private boolean isOnlySelect;
    private ArrayList<Long> selectedDialogs;
    private boolean hasChatlistHint;
    private int currentAccount;
    private boolean dialogsListFrozen;
    private boolean isReordering;
    private boolean collapsedView;
    RecyclerListView recyclerListView;
    ArrayList<ItemInternal> itemInternals = new ArrayList<>();
    ArrayList<ItemInternal> oldItems = new ArrayList<>();

    private DialogsPreloader preloader;

    private SavedChannelsActivity parentFragment;
    private boolean isTransitionSupport;

    // SavedChannels-specific data model
    ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    Set<String> failedLoadChats = new HashSet<>();
    private boolean chatLoading = false;
    android.util.LongSparseArray<ArrayList<MessageObject>> messageMap = new android.util.LongSparseArray<>();

    private TLRPC.RequestPeerType requestPeerType;
    public boolean isEmpty;

    public SavedChannelsAdapter(SavedChannelsActivity fragment, Context context, int type, int folder, boolean onlySelect, ArrayList<Long> selected, int account, TLRPC.RequestPeerType requestPeerType) {
        mContext = context;
        parentFragment = fragment;
        isOnlySelect = onlySelect;
        selectedDialogs = selected;
        currentAccount = account;
        this.preloader = new DialogsPreloader();
        this.requestPeerType = requestPeerType;
    }

    public void setRecyclerListView(RecyclerListView recyclerListView) {
        this.recyclerListView = recyclerListView;
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public void onReorderStateChanged(boolean reordering) {
        isReordering = reordering;
    }

    public int fixPosition(int position) {
        if (hasChatlistHint) {
            position--;
        }
        return position;
    }

    public boolean isDataSetChanged() {
        return true;
    }

    public int getDialogsCount() {
        return dialogsCount;
    }

    @Override
    public long getItemId(int position) {
        return itemInternals.get(position).stableId;
    }

    @Override
    public int getItemCount() {
        currentCount = itemInternals.size();
        return currentCount;
    }

    public int findDialogPosition(long dialogId) {
        for (int i = 0; i < itemInternals.size(); i++) {
            if (itemInternals.get(i).chat != null && -itemInternals.get(i).chat.id == dialogId) {
                return i;
            }
        }
        return -1;
    }

    public int fixScrollGap(RecyclerListView animationSupportListView, int p, int offset) {
        int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 76 : 70);
        int top = offset + animationSupportListView.getPaddingTop() - p * cellHeight - p;
        int paddingTop = animationSupportListView.getPaddingTop();
        if (top > paddingTop) {
            return offset + paddingTop - top;
        }
        return offset;
    }

    int stableIdPointer = 10;
    LongSparseIntArray dialogsStableIds = new LongSparseIntArray();

    private class ItemInternal extends AdapterWithDiffUtils.Item {

        TLRPC.Chat chat;
        TLRPC.RecentMeUrl recentMeUrl;
        boolean isForumCell;
        private boolean pinned;
        private int emptyType;

        private final int stableId;

        public ItemInternal(int viewType, TLRPC.Chat chat) {
            super(viewType, true);
            this.chat = chat;
            if (chat != null) {
                int currentId = dialogsStableIds.get(-chat.id, -1);
                if (currentId >= 0) {
                    stableId = currentId;
                } else {
                    stableId = stableIdPointer++;
                    dialogsStableIds.put(-chat.id, stableId);
                }
            } else {
                stableId = stableIdPointer++;
            }
            if (chat != null) {
                pinned = UserConfig.getInstance(currentAccount).pinnedSavedChannels.contains(chat.username);
                isForumCell = chat.forum;
            }
        }

        public ItemInternal(int viewTypeEmpty) {
            super(viewTypeEmpty, true);
            this.emptyType = viewTypeEmpty;
            if (viewTypeEmpty == VIEW_TYPE_LAST_EMPTY) {
                stableId = 1;
            } else {
                stableId = stableIdPointer++;
            }
        }

        public ItemInternal(int viewTypeEmpty, int emptyType) {
            super(viewTypeEmpty, true);
            this.emptyType = emptyType;
            stableId = stableIdPointer++;
        }

        boolean compare(ItemInternal itemInternal) {
            if (viewType != itemInternal.viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_DIALOG) {
                return chat != null && itemInternal.chat != null && chat.id == itemInternal.chat.id &&
                        isForumCell == itemInternal.isForumCell &&
                        pinned == itemInternal.pinned;
            }
            if (viewType == VIEW_TYPE_EMPTY) {
                return emptyType == itemInternal.emptyType;
            }
            if (viewType == VIEW_TYPE_LAST_EMPTY) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(chat, recentMeUrl);
        }
    }

    public TLObject getItem(int i) {
        if (i < 0 || i >= itemInternals.size()) {
            return null;
        }
        if (itemInternals.get(i).chat != null) {
            return itemInternals.get(i).chat;
        } else if (itemInternals.get(i).recentMeUrl != null) {
            return itemInternals.get(i).recentMeUrl;
        }
        return null;
    }

    public void setDialogsListFrozen(boolean frozen) {
        dialogsListFrozen = frozen;
    }

    public boolean getDialogsListIsFrozen() {
        return dialogsListFrozen;
    }

    public void updateHasHints() {
    }

    boolean isCalculatingDiff;
    boolean updateListPending;
    private final static boolean ALLOW_UPDATE_IN_BACKGROUND = BuildVars.DEBUG_PRIVATE_VERSION;

    public void updateList(Runnable saveScrollPosition) {
        if (isCalculatingDiff) {
            updateListPending = true;
            return;
        }
        isCalculatingDiff = true;
        oldItems = new ArrayList<>();
        oldItems.addAll(itemInternals);
        updateItemList();
        ArrayList<ItemInternal> newItems = new ArrayList<>(itemInternals);
        itemInternals = oldItems;

        DiffUtil.Callback callback = new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).compare(newItems.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ItemInternal oldItem = oldItems.get(oldItemPosition);
                ItemInternal newItem = newItems.get(newItemPosition);
                if (oldItem.viewType == VIEW_TYPE_DIALOG && oldItem.chat != null && newItem.chat != null) {
                    ArrayList<MessageObject> oldMsgs = messageMap.get(-oldItem.chat.id);
                    ArrayList<MessageObject> newMsgs = messageMap.get(-newItem.chat.id);
                    int oldMsgId = oldMsgs != null && !oldMsgs.isEmpty() ? oldMsgs.get(0).getId() : 0;
                    int newMsgId = newMsgs != null && !newMsgs.isEmpty() ? newMsgs.get(0).getId() : 0;
                    return oldMsgId == newMsgId && oldItem.chat.id == newItem.chat.id;
                }
                return oldItem.viewType == newItem.viewType;
            }
        };
        if (itemInternals.size() < 50 || !ALLOW_UPDATE_IN_BACKGROUND) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);
            isCalculatingDiff = false;
            if (saveScrollPosition != null) {
                saveScrollPosition.run();
            }
            itemInternals = newItems;
            result.dispatchUpdatesTo(this);
        } else {
            Utilities.searchQueue.postRunnable(() -> {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);
                AndroidUtilities.runOnUIThread(() -> {
                    if (!isCalculatingDiff) {
                        return;
                    }
                    isCalculatingDiff = false;
                    if (saveScrollPosition != null) {
                        saveScrollPosition.run();
                    }
                    itemInternals = newItems;
                    result.dispatchUpdatesTo(this);
                    if (updateListPending) {
                        updateListPending = false;
                        updateList(saveScrollPosition);
                    }
                });
            });
        }

    }

    @Override
    public void notifyDataSetChanged() {
        if (isCalculatingDiff) {
            itemInternals = new ArrayList<>();
        }
        isCalculatingDiff = false;
        updateItemList();
        super.notifyDataSetChanged();
    }


    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof SavedChannelCell) {
            SavedChannelCell dialogCell = (SavedChannelCell) holder.itemView;
            dialogCell.onReorderStateChanged(isReordering, false);
            dialogCell.checkCurrentDialogIndex(dialogsListFrozen);
            dialogCell.setChecked(selectedDialogs.contains(dialogCell.getDialogId()), false);
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        return viewType != VIEW_TYPE_FLICKER && viewType != VIEW_TYPE_EMPTY && viewType != VIEW_TYPE_LAST_EMPTY &&
                viewType != VIEW_TYPE_REQUIREMENTS && viewType != VIEW_TYPE_REQUIRED_EMPTY;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_DIALOG:
                SavedChannelCell dialogCell = new SavedChannelCell(parentFragment, mContext, true, false, currentAccount, null);
                dialogCell.setPreloader(preloader);
                dialogCell.setDialogCellDelegate(this);
                dialogCell.setIsTransitionSupport(isTransitionSupport);
                view = dialogCell;
                break;
            case VIEW_TYPE_REQUIREMENTS:
                view = new RequestPeerRequirementsCell(mContext);
                break;
            case VIEW_TYPE_FLICKER:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                flickerLoadingView.setIsSingleCell(true);
                int flickerType = FlickerLoadingView.DIALOG_CELL_TYPE;
                flickerLoadingView.setViewType(flickerType);
                if (flickerType == FlickerLoadingView.CONTACT_TYPE) {
                    flickerLoadingView.setIgnoreHeightCheck(true);
                }
                view = flickerLoadingView;
                break;
            case VIEW_TYPE_EMPTY:
                view = new DialogsEmptyCell(mContext);
                break;
            case VIEW_TYPE_REQUIRED_EMPTY:
                view = new DialogsRequestedEmptyCell(mContext) {
                    @Override
                    protected void onButtonClick() {
                        onCreateGroupForThisClick();
                    }
                };
                break;
            case VIEW_TYPE_LAST_EMPTY: {
                view = new LastEmptyView(mContext);
                break;
            }
            default: {
                view = new TextCell(mContext);
                break;
            }
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, viewType == VIEW_TYPE_EMPTY ? RecyclerView.LayoutParams.MATCH_PARENT : RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    public void onCreateGroupForThisClick() {

    }

    public int lastDialogsEmptyType = -1;

    public int dialogsEmptyType() {
        return DialogsEmptyCell.TYPE_FILTER_NO_CHATS_TO_DISPLAY;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_DIALOG: {
                TLRPC.Chat chat = (TLRPC.Chat) getItem(i);
                SavedChannelCell cell = (SavedChannelCell) holder.itemView;
                TLRPC.Chat nextChat = (TLRPC.Chat) getItem(i + 1);
                cell.useSeparator = nextChat != null;
                if (AndroidUtilities.isTablet()) {
                    cell.setDialogSelected(-chat.id == openedDialogId);
                }
                cell.setChecked(selectedDialogs.contains(-chat.id), false);
                boolean pinned = UserConfig.getInstance(currentAccount).pinnedSavedChannels.contains(chat.username);
                cell.setChat(chat, pinned, messageMap.get(-chat.id));
                cell.checkHeight();
                if (cell.collapsed != collapsedView) {
                    cell.collapsed = collapsedView;
                    cell.requestLayout();
                }
                if (preloader != null && i < 10) {
                    preloader.add(-chat.id);
                }
                break;
            }
            case VIEW_TYPE_EMPTY: {
                DialogsEmptyCell cell = (DialogsEmptyCell) holder.itemView;
                int fromDialogsEmptyType = lastDialogsEmptyType;
                cell.setType(lastDialogsEmptyType = dialogsEmptyType(), isOnlySelect);
                break;
            }
            case VIEW_TYPE_REQUIRED_EMPTY: {
                ((DialogsRequestedEmptyCell) holder.itemView).set(requestPeerType);
                break;
            }
            case VIEW_TYPE_REQUIREMENTS: {
                RequestPeerRequirementsCell cell = (RequestPeerRequirementsCell) holder.itemView;
                cell.set(requestPeerType);
                break;
            }
        }
        if (i >= dialogsCount + 1) {
            holder.itemView.setAlpha(1f);
        }
    }

    public TL_chatlists.TL_chatlists_chatlistUpdates getChatlistUpdate() {
        return null;
    }

    @Override
    public int getItemViewType(int i) {
        return itemInternals.get(i).viewType;
    }

    @Override
    public void notifyItemMoved(int fromPosition, int toPosition) {
        super.notifyItemMoved(fromPosition, toPosition);
    }

    public void didDatabaseCleared() {
        if (preloader != null) {
            preloader.clear();
        }
    }

    public void resume() {
        if (preloader != null) {
            preloader.resume();
        }
    }

    public void pause() {
        if (preloader != null) {
            preloader.pause();
        }
    }

    @Override
    public void onButtonClicked(SavedChannelCell dialogCell) {

    }

    @Override
    public void onButtonLongPress(SavedChannelCell dialogCell) {

    }

    @Override
    public boolean canClickButtonInside() {
        return selectedDialogs.isEmpty();
    }

    @Override
    public void openStory(SavedChannelCell dialogCell, Runnable onDone) {
        if (MessagesController.getInstance(currentAccount).getStoriesController().hasStories(dialogCell.getDialogId())) {
            parentFragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
            parentFragment.getOrCreateStoryViewer().open(parentFragment.getContext(), dialogCell.getDialogId(), StoriesListPlaceProvider.of((RecyclerListView) dialogCell.getParent()));
        }
    }

    @Override
    public void showChatPreview(SavedChannelCell cell) {
        parentFragment.showChatPreview(cell);
    }

    @Override
    public void openHiddenStories() {
        StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        if (storiesController.getHiddenList().isEmpty()) {
            return;
        }
        boolean unreadOnly = storiesController.getUnreadState(DialogObject.getPeerDialogId(storiesController.getHiddenList().get(0).peer)) != StoriesController.STATE_READ;
        ArrayList<Long> peerIds = new ArrayList<>();
        for (int i = 0; i < storiesController.getHiddenList().size(); i++) {
            long dialogId = DialogObject.getPeerDialogId(storiesController.getHiddenList().get(i).peer);
            if (!unreadOnly || storiesController.getUnreadState(dialogId) != StoriesController.STATE_READ) {
                peerIds.add(dialogId);
            }
        }

        parentFragment.getOrCreateStoryViewer().open(mContext, null, peerIds, 0, null, null, StoriesListPlaceProvider.of(recyclerListView, true), false);
    }

    public void setIsTransitionSupport() {
        this.isTransitionSupport = true;
    }

    public void setCollapsedView(boolean collapsedView, RecyclerListView listView) {
        this.collapsedView = collapsedView;
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i) instanceof SavedChannelCell) {
                ((SavedChannelCell) listView.getChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getCachedChildCount(); i++) {
            if (listView.getCachedChildAt(i) instanceof SavedChannelCell) {
                ((SavedChannelCell) listView.getCachedChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getHiddenChildCount(); i++) {
            if (listView.getHiddenChildAt(i) instanceof SavedChannelCell) {
                ((SavedChannelCell) listView.getHiddenChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getAttachedScrapChildCount(); i++) {
            if (listView.getAttachedScrapChildAt(i) instanceof SavedChannelCell) {
                ((SavedChannelCell) listView.getAttachedScrapChildAt(i)).collapsed = collapsedView;
            }
        }
    }

    public static class DialogsPreloader {

        private final int MAX_REQUEST_COUNT = 4;
        private final int MAX_NETWORK_REQUEST_COUNT = 10 - MAX_REQUEST_COUNT;
        private final int NETWORK_REQUESTS_RESET_TIME = 60_000;

        HashSet<Long> dialogsReadyMap = new HashSet<>();
        HashSet<Long> preloadedErrorMap = new HashSet<>();

        HashSet<Long> loadingDialogs = new HashSet<>();
        ArrayList<Long> preloadDialogsPool = new ArrayList<>();
        int currentRequestCount;
        int networkRequestCount;

        boolean resumed;

        Runnable clearNetworkRequestCount = () -> {
            networkRequestCount = 0;
            start();
        };

        public void add(long dialog_id) {
            if (isReady(dialog_id) || preloadedErrorMap.contains(dialog_id) || loadingDialogs.contains(dialog_id) || preloadDialogsPool.contains(dialog_id)) {
                return;
            }
            preloadDialogsPool.add(dialog_id);
            start();
        }

        private void start() {
            if (!preloadIsAvilable() || !resumed || preloadDialogsPool.isEmpty() || currentRequestCount >= MAX_REQUEST_COUNT || networkRequestCount > MAX_NETWORK_REQUEST_COUNT) {
                return;
            }
            long dialog_id = preloadDialogsPool.remove(0);
            currentRequestCount++;
            loadingDialogs.add(dialog_id);
            MessagesController.getInstance(UserConfig.selectedAccount).ensureMessagesLoaded(dialog_id, 0, new MessagesController.MessagesLoadedCallback() {
                @Override
                public void onMessagesLoaded(boolean fromCache) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!fromCache) {
                            networkRequestCount++;
                            if (networkRequestCount >= MAX_NETWORK_REQUEST_COUNT) {
                                AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount);
                                AndroidUtilities.runOnUIThread(clearNetworkRequestCount, NETWORK_REQUESTS_RESET_TIME);
                            }
                        }
                        if (loadingDialogs.remove(dialog_id)) {
                            dialogsReadyMap.add(dialog_id);
                            updateList();
                            currentRequestCount--;
                            start();
                        }
                    });
                }

                @Override
                public void onError() {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (loadingDialogs.remove(dialog_id)) {
                            preloadedErrorMap.add(dialog_id);
                            currentRequestCount--;
                            start();
                        }
                    });
                }
            });
        }

        private boolean preloadIsAvilable() {
            return false;
            // return DownloadController.getInstance(UserConfig.selectedAccount).getCurrentDownloadMask() != 0;
        }

        public void updateList() {
        }

        public boolean isReady(long currentDialogId) {
            return dialogsReadyMap.contains(currentDialogId);
        }

        public void remove(long currentDialogId) {
            preloadDialogsPool.remove(currentDialogId);
        }

        public void clear() {
            dialogsReadyMap.clear();
            preloadedErrorMap.clear();
            loadingDialogs.clear();
            preloadDialogsPool.clear();
            currentRequestCount = 0;
            networkRequestCount = 0;
            AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount);
            updateList();
        }

        public void resume() {
            resumed = true;
            start();
        }

        public void pause() {
            resumed = false;
        }
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public class LastEmptyView extends FrameLayout {

        public boolean moving;

        public LastEmptyView(Context context) {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = itemInternals.size();
            View parent = (View) getParent();
            int height;
            int blurOffset = 0;
            if (parent instanceof BlurredRecyclerView) {
                blurOffset = ((BlurredRecyclerView) parent).blurTopPadding;
            }
            boolean collapsedView = SavedChannelsAdapter.this.collapsedView;
            int paddingTop = parent.getPaddingTop();
            int paddingBottom = parent.getPaddingBottom();
            paddingTop -= blurOffset;
            if (size == 0 || paddingTop == 0) {
                height = 0;
            } else {
                height = MeasureSpec.getSize(heightMeasureSpec);
                if (height == 0) {
                    height = parent.getMeasuredHeight();
                }
                if (height == 0) {
                    height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight;
                }
                height -= blurOffset;
                int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 76 : 70);
                int dialogsHeight = 0;
                for (int i = 0; i < size; i++) {
                    if (itemInternals.get(i).viewType == VIEW_TYPE_DIALOG) {
                        if (itemInternals.get(i).isForumCell && !collapsedView) {
                            dialogsHeight += AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 86 : 91);
                        } else {
                            dialogsHeight += cellHeight;
                        }
                    } else  if (itemInternals.get(i).viewType == VIEW_TYPE_FLICKER) {
                        dialogsHeight += cellHeight;
                    }
                }
                dialogsHeight += size - 1;
                if (dialogsHeight < height) {
                    height = height - dialogsHeight - paddingBottom;
                    if (paddingTop != 0) {
                        height -= AndroidUtilities.statusBarHeight;
                        if (/*parentFragment.hasStories &&*/ !collapsedView && !isTransitionSupport) {
                            height -= ActionBar.getCurrentActionBarHeight();
                            if (getParent() instanceof SavedChannelsActivity.DialogsRecyclerView) {
                                SavedChannelsActivity.DialogsRecyclerView dialogsRecyclerView = (SavedChannelsActivity.DialogsRecyclerView) getParent();
                                height -= dialogsRecyclerView.additionalPadding;
                            }
                        } else if (collapsedView) {
                            height -= paddingTop;
                        }
                    }
                } else {
                    height = 0;
                }
            }
            if (height < 0) {
                height = 0;
            }
            if (isTransitionSupport) {
                height += AndroidUtilities.dp(1000);
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }


    private void updateItemList() {
        boolean containedEmptyViewBefore = itemInternals.stream().anyMatch(item -> item.viewType == VIEW_TYPE_EMPTY);
        itemInternals.clear();
        updateHasHints();

        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        dialogsCount = chats.size();
        isEmpty = false;

        hasChatlistHint = false;

        if (requestPeerType != null) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_REQUIREMENTS));
        }

        if (collapsedView || isTransitionSupport) {
            for (int k = 0; k < chats.size(); k++) {
                itemInternals.add(new ItemInternal(VIEW_TYPE_DIALOG, chats.get(k)));
            }
            itemInternals.add(new ItemInternal(VIEW_TYPE_LAST_EMPTY));
            return;
        }

        for (int k = 0; k < chats.size(); k++) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_DIALOG, chats.get(k)));
        }

        if (hasUnloadedChats()) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_FLICKER));
        }

        if (dialogsCount == 0 && !hasUnloadedChats()) {
            isEmpty = true;
            itemInternals.add(new ItemInternal(VIEW_TYPE_EMPTY, dialogsEmptyType()));
        } else {
            itemInternals.add(new ItemInternal(VIEW_TYPE_LAST_EMPTY));
        }

        if (!messagesController.hiddenUndoChats.isEmpty()) {
            for (int i = 0; i < itemInternals.size(); ++i) {
                ItemInternal item = itemInternals.get(i);
                if (item.viewType == VIEW_TYPE_DIALOG && item.chat != null && messagesController.isHiddenByUndo(-item.chat.id)) {
                    itemInternals.remove(i);
                    i--;
                }
            }
        }
        boolean containsEmptyViewNow = itemInternals.stream().anyMatch(item -> item.viewType == VIEW_TYPE_EMPTY);
        if (MessagesStorage.getInstance(currentAccount).fileProtectionEnabled() && containedEmptyViewBefore && !containsEmptyViewNow) {
            AndroidUtilities.runOnUIThread(() -> parentFragment.setScrollDisabled(false));
        }
    }

    public int getItemHeight(int position) {
        if (itemInternals.get(position).viewType == VIEW_TYPE_DIALOG) {
            if (itemInternals.get(position).isForumCell && !collapsedView) {
                return AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 86 : 91) + 1;
            } else {
                return AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 76 : 70) + 1;
            }
        }
        return 0;
    }

    protected void onOpenBot(TLRPC.User user) {

    }

    // ---- SavedChannels data loading (ported from SavedChannelsAdapterOld) ----

    public void loadChats() {
        Set<String> names = UserConfig.getInstance(currentAccount).savedChannels;
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                addChat((TLRPC.Chat) obj);
            }
        }
        loadOtherChats();
    }

    private boolean hasUnloadedChats() {
        Set<String> names = new HashSet<>(UserConfig.getInstance(currentAccount).savedChannels);
        MessagesController controller = MessagesController.getInstance(currentAccount);
        Set<String> loaded = new HashSet<>();
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                loaded.add(name);
            }
        }
        names.removeAll(loaded);
        names.removeAll(failedLoadChats);
        return !names.isEmpty();
    }

    private void loadOtherChats() {
        Set<String> names = new HashSet<>(UserConfig.getInstance(currentAccount).savedChannels);
        Set<String> existedNames = new HashSet<>();
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                existedNames.add(name);
            }
        }
        names.removeAll(existedNames);
        names.removeAll(failedLoadChats);
        if (!names.isEmpty() && !chatLoading) {
            chatLoading = true;
            String username = names.iterator().next();
            Utilities.globalQueue.postRunnable(() -> resolveUsername(username), 1000);
        }
    }

    private void addChat(TLRPC.Chat chat) {
        int position = getInsertPosition(chats, chat);
        if (position < 0) {
            MessagesController.getInstance(currentAccount).loadMessages(-chat.id, 0, false, 20, 0, 0, false, 0, 0, 2, 0, 0, 0, 0, 1, false);
            int insertPosition = -(position + 1);
            chats.add(insertPosition, chat);
            updateList(null);
        }
    }

    private int getInsertPosition(List<TLRPC.Chat> list, TLRPC.Chat chat) {
        List<String> pinnedChannels = UserConfig.getInstance(currentAccount).pinnedSavedChannels;
        int position = Collections.binarySearch(list, chat, (a, b) -> {
            int aIndex = pinnedChannels.indexOf(a.username);
            int bIndex = pinnedChannels.indexOf(b.username);
            if (aIndex == -1 && bIndex == -1) {
                ArrayList<MessageObject> aMsgs = messageMap.get(-a.id);
                ArrayList<MessageObject> bMsgs = messageMap.get(-b.id);
                MessageObject aMessage = aMsgs != null && !aMsgs.isEmpty() ? aMsgs.get(0) : null;
                MessageObject bMessage = bMsgs != null && !bMsgs.isEmpty() ? bMsgs.get(0) : null;
                if (aMessage != null && bMessage != null) {
                    return bMessage.messageOwner.date - aMessage.messageOwner.date;
                } else {
                    return Boolean.compare(aMessage == null, bMessage == null);
                }
            } else {
                aIndex = aIndex != -1 ? aIndex : pinnedChannels.size();
                bIndex = bIndex != -1 ? bIndex : pinnedChannels.size();
                return aIndex - bIndex;
            }
        });
        if (position >= 0 && list.get(position).id != chat.id) {
            position = -list.size() - 1;
        }
        return position;
    }

    public void fixChatPosition(String userName) {
        if (chats != null) {
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).username != null && chats.get(i).username.equals(userName)) {
                    fixChatPosition(i);
                }
            }
        }
    }

    public void fixChatPosition(int oldPosition) {
        TLRPC.Chat chat = chats.get(oldPosition);
        chats.remove(oldPosition);
        int position = getInsertPosition(chats, chat);
        if (position < 0) {
            int insertPosition = -(position + 1);
            chats.add(insertPosition, chat);
        }
        updateList(null);
    }

    public MessageObject getMessage(long dialogId) {
        ArrayList<MessageObject> msgs = messageMap.get(dialogId);
        return msgs != null && !msgs.isEmpty() ? msgs.get(0) : null;
    }

    public void messagesDidLoad(long dialogId, List<MessageObject> messages) {
        if (chats.stream().anyMatch(c -> c.id == -dialogId)) {
            MessageObject topMessage = null;
            for (MessageObject m : messages) {
                if (topMessage == null || m.getId() > topMessage.getId()) {
                    topMessage = m;
                }
            }
            if (topMessage != null) {
                ArrayList<MessageObject> existing = messageMap.get(dialogId);
                MessageObject existingTop = existing != null && !existing.isEmpty() ? existing.get(0) : null;
                if (existingTop == null || topMessage.getId() > existingTop.getId()) {
                    ArrayList<MessageObject> group = new ArrayList<>();
                    group.add(topMessage);
                    if (topMessage.messageOwner.grouped_id != 0) {
                        for (MessageObject m : messages) {
                            if (m != topMessage && m.messageOwner.grouped_id == topMessage.messageOwner.grouped_id) {
                                group.add(m);
                            }
                        }
                    }
                    messageMap.put(dialogId, group);
                }
            }
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).id == -dialogId) {
                    fixChatPosition(i);
                    break;
                }
            }
        }
        notifyItemChanged(findDialogPosition(dialogId));
    }

    private void resolveUsername(String username) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                chatLoading = false;
                if (response != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                        if (res.chats.size() == 1) {
                            addChat(res.chats.get(0));
                        }
                        loadOtherChats();
                    });
                } else {
                    synchronized (this) {
                        failedLoadChats.add(username);
                    }
                    loadOtherChats();
                }
            },
            ConnectionsManager.RequestFlagFailOnServerErrors
        );
    }

    public void moveSavedChannelDialogs(int fromPosition, int toPosition) {
        int fromIndex = fixPosition(fromPosition);
        int toIndex = fixPosition(toPosition);
        if (fromIndex < 0 || fromIndex >= chats.size() || toIndex < 0 || toIndex >= chats.size()) {
            return;
        }
        TLRPC.Chat fromChat = chats.get(fromIndex);
        TLRPC.Chat toChat = chats.get(toIndex);
        List<String> pinnedChannels = UserConfig.getInstance(currentAccount).pinnedSavedChannels;
        int fromPinIdx = fromChat.username != null ? pinnedChannels.indexOf(fromChat.username) : -1;
        int toPinIdx = toChat.username != null ? pinnedChannels.indexOf(toChat.username) : -1;
        if (fromPinIdx >= 0 && toPinIdx >= 0) {
            Collections.swap(pinnedChannels, fromPinIdx, toPinIdx);
            UserConfig.getInstance(currentAccount).saveConfig(true);
        }
        Collections.swap(chats, fromIndex, toIndex);
        updateList(null);
    }

    public void removeItems(List<String> selectedUsernames) {
        chats = chats.stream().filter(c -> !selectedUsernames.contains(c.username)).collect(Collectors.toCollection(ArrayList::new));
        updateList(null);
    }

    public List<String> getSelectedUserNames() {
        return chats.stream().filter(c -> selectedDialogs.contains(-c.id))
                .map(c -> c.username).collect(Collectors.toList());
    }

    public ArrayList<TLRPC.Chat> getChats() {
        return chats;
    }

    public MessageObject getTopMessage(long dialogId) {
        ArrayList<MessageObject> msgs = messageMap.get(dialogId);
        return msgs != null && !msgs.isEmpty() ? msgs.get(0) : null;
    }

    public ArrayList<MessageObject> getGroupMessages(long dialogId) {
        return messageMap.get(dialogId);
    }
}
