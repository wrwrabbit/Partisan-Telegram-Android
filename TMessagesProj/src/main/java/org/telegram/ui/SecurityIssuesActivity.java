/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PrivacyChecker;
import org.telegram.messenger.partisan.SecurityChecker;
import org.telegram.messenger.partisan.SecurityIssue;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.SecurityIssueCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SecurityIssuesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ActionBarMenuItem otherItem;
    private final boolean[] oldShowSuggestionValues = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private final static int reset_issues = 1;

    public SecurityIssuesActivity() {
        super();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            oldShowSuggestionValues[a] = config.showSecuritySuggestions;
        }
    }


    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.securityIssuesChanged);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.securityIssuesChanged);
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

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString(R.string.SecurityIssuesTitle));
        ActionBarMenu menu = actionBar.createMenu();
        otherItem = menu.addItem(10, R.drawable.ic_ab_other);
        otherItem.addSubItem(reset_issues, LocaleController.getString(R.string.ResetIgnoredIssues));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == reset_issues) {
                    getUserConfig().ignoredSecurityIssues.clear();
                    SharedConfig.ignoredSecurityIssues.clear();
                    updateShowSuggestion();
                    getUserConfig().saveConfig(false);
                    if (listAdapter != null) {
                        listAdapter.updateIssues();
                        listAdapter.notifyDataSetChanged();
                    }
                    checkResetIssuesItemVisibility();
                }
            }
        });
        checkResetIssuesItemVisibility();

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

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SecurityChecker.checkSecurityIssuesAndSave(getParentActivity(), getCurrentAccount(), true);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void showPrivacyErrorAlert() {
        if (getContext() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(LocaleController.getString("PrivacyFloodControlError", R.string.PrivacyFloodControlError));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }

    private void showFixed() {
        if (getContext() == null) {
            return;
        }
        Toast.makeText(getContext(), LocaleController.getString(R.string.FixedToast), Toast.LENGTH_LONG).show();
    }

    private void checkResetIssuesItemVisibility() {
        if (getUserConfig().getIgnoredSecurityIssues().isEmpty()) {
            otherItem.setVisibility(View.GONE);
        } else {
            otherItem.setVisibility(View.VISIBLE);
        }
    }

    private void updateShowSuggestion() {
        boolean oldShow = getUserConfig().showSecuritySuggestions;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            config.showSecuritySuggestions = !getUserConfig().getIgnoredSecurityIssues()
                    .containsAll(getUserConfig().currentSecurityIssues)
                    && oldShowSuggestionValues[a]; // true only if it was true when the activity started
            config.saveConfig(false);
        }
        boolean currentAccountShowSuggestionChanged = getUserConfig().showSecuritySuggestions != oldShow;
        if (currentAccountShowSuggestionChanged) {
            getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.securityIssuesChanged) {
            if (listAdapter != null) {
                listAdapter.updateIssues();
                listAdapter.notifyDataSetChanged();
            }
            checkResetIssuesItemVisibility();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private List<SecurityIssue> securityIssues;
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
            updateIssues();
        }

        public void updateIssues() {
            securityIssues = new ArrayList<>(getUserConfig().getActiveSecurityIssues());
            Collections.sort(securityIssues);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return securityIssues.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    view = new SecurityIssueCell(mContext) {
                        @Override
                        protected void onCloseClick() {
                            if (currentIssue.isGlobal()) {
                                SharedConfig.ignoredSecurityIssues.add(currentIssue);
                            } else {
                                getUserConfig().ignoredSecurityIssues.add(currentIssue);
                            }
                            updateShowSuggestion();
                            getUserConfig().saveConfig(false);
                            int issueIndex = ListAdapter.this.securityIssues.indexOf(currentIssue);
                            ListAdapter.this.securityIssues.remove(currentIssue);
                            ListAdapter.this.notifyItemRemoved(issueIndex);
                            checkResetIssuesItemVisibility();
                        }

                        @Override
                        protected void onFixClick() {
                            if (currentIssue == SecurityIssue.PRIVACY) {
                                PrivacyChecker.fix(currentAccount, SecurityIssuesActivity.this::showPrivacyErrorAlert, () -> fixed(currentIssue));
                            } else if (currentIssue == SecurityIssue.TWO_STEP_VERIFICATION) {
                                TwoStepVerificationSetupActivity twoStepVerification = new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null);
                                twoStepVerification.setFromRegistration(false);
                                twoStepVerification.returnToSecurityIssuesActivity = true;
                                presentFragment(twoStepVerification, false);
                            }
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    SecurityIssueCell securityCell = (SecurityIssueCell) holder.itemView;
                    securityCell.setIssue(securityIssues.get(position), position != securityIssues.size() - 1);
                    securityCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        private void fixed(SecurityIssue issue) {
            getUserConfig().currentSecurityIssues.remove(issue);
            updateShowSuggestion();
            getUserConfig().saveConfig(false);
            int index = securityIssues.indexOf(issue);
            securityIssues.remove(issue);
            notifyItemRemoved(index);
            showFixed();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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

        return themeDescriptions;
    }
}
