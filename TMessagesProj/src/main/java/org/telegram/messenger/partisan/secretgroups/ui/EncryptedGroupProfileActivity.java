package org.telegram.messenger.partisan.secretgroups.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.messenger.partisan.secretgroups.InnerEncryptedChat;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.AllowShowingActivityInterface;
import org.telegram.ui.Cells.EncryptedGroupMemberCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

public class EncryptedGroupProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AllowShowingActivityInterface {
    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private ActionBarMenuItem editItem;

    private final static int edit_group = 1;

    private int rowCount;

    private int firstMemberRow;
    private int lastMemberRow;

    private final EncryptedGroup encryptedGroup;

    public EncryptedGroupProfileActivity(EncryptedGroup encryptedGroup) {
        super();
        this.encryptedGroup = encryptedGroup;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsHidingChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
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
                } else if (id == edit_group) {
                    EncryptedGroupEditActivity fragment = new EncryptedGroupEditActivity(encryptedGroup);
                    presentFragment(fragment);
                }
            }
        });

        if (encryptedGroup.getOwnerUserId() == getUserConfig().clientUserId) {
            ActionBarMenu menu = actionBar.createMenu();
            editItem = menu.addItem(edit_group, R.drawable.group_edit_profile);
            editItem.setContentDescription(LocaleController.getString(R.string.Edit));
        }

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(encryptedGroup.getName());
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
            if (firstMemberRow != -1 && firstMemberRow <= position && position <= lastMemberRow) {
                Bundle args = new Bundle();
                int index = positionToChatIndex(position);
                TLRPC.User user = getUser(index);
                if (user == null) {
                    return;
                }
                args.putLong("user_id", user.id);
                args.putLong("dialog_id", getDialogId(index));
                args.putBoolean("reportSpam", false);
                args.putInt("actionBarColor", getThemedColor(Theme.key_actionBarDefault));
                ProfileActivity fragment = new ProfileActivity(args);
                presentFragment(fragment);
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;

        firstMemberRow = rowCount;
        lastMemberRow = firstMemberRow + encryptedGroup.getInnerChats().size();
        rowCount = lastMemberRow + 1;
    }

    private TLRPC.User getUser(int index) {
        if (index < 0 || index >= encryptedGroup.getInnerChats().size()) {
            return null;
        }
        long userId = encryptedGroup.getInnerChats().get(index).getUserId();
        return getMessagesController().getUser(userId);
    }

    private InnerEncryptedChat getInnerChat(int index) {
        if (index >= encryptedGroup.getInnerChats().size()) {
            return null;
        }
        return encryptedGroup.getInnerChats().get(index);
    }

    private long getDialogId(int index) {
        if (index >= encryptedGroup.getInnerChats().size()) {
            return 0;
        }
        return encryptedGroup.getInnerChats().get(index).getDialogId().orElse(0L);
    }

    @Override
    public boolean allowShowing() {
        return !FakePasscodeUtils.isFakePasscodeActivated();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!allowShowing()) {
            finishHiddenChatFragment();
            return;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.dialogsHidingChanged) {
            if (!allowShowing()) {
                finishHiddenChatFragment();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            boolean infoChanged = (mask & MessagesController.UPDATE_MASK_NAME) != 0;
            if (infoChanged) {
                actionBar.setTitle(encryptedGroup.getName());
            }
        }
    }

    private void finishHiddenChatFragment() {
        if (!finishing) {
            super.finishFragment(false);
        }
    }

    private int positionToChatIndex(int position) {
        return position - firstMemberRow - 1;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_GROUP_MEMBER = 0;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return firstMemberRow <= position && position <= lastMemberRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_GROUP_MEMBER:
                default:
                {
                    view = new EncryptedGroupMemberCell(mContext, encryptedGroup, currentAccount);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_GROUP_MEMBER: {
                    EncryptedGroupMemberCell cell = (EncryptedGroupMemberCell) holder.itemView;
                    if (position == firstMemberRow) {
                        cell.setUserAndInnerChat(getUserConfig().getCurrentUser(), null, position != lastMemberRow);
                    } else {
                        int index = positionToChatIndex(position);
                        TLRPC.User user = getUser(index);
                        InnerEncryptedChat innerChat = getInnerChat(index);
                        cell.setUserAndInnerChat(user, innerChat, position != lastMemberRow);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (firstMemberRow <= position && position <= lastMemberRow) {
                return VIEW_TYPE_GROUP_MEMBER;
            }
            return VIEW_TYPE_GROUP_MEMBER;
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
