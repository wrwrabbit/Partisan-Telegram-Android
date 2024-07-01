/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.AccountActions;
import org.telegram.messenger.fakepasscode.CheckedSessions;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.TerminateOtherSessionsAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;
import org.telegram.ui.RemoveChatsAction.RemoveChatsFragment;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FakePasscodeAccountActionsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private AccountActions actions;
    private FakePasscode fakePasscode;

    private int rowCount;

    private int changeTelegramMessageRow;
    private int messagesDetailRow;

    private int changePhoneRow;
    private int phoneDetailRow;

    private int changeChatsToRemoveRow;
    private int changeChatsToRemoveDetailRow;
    private int deleteAllContactsRow;
    private int deleteAllStickersRow;
    private int clearSearchHistoryRow;
    private int clearBlackListRow;
    private int clearSavedChannelsRow;
    private int clearDraftsRow;
    private int logOutRow;
    private int hideAccountRow;
    private int sessionsToTerminateRow;
    private int sessionsToHideRow;
    private int sessionsSettingsDetailRow;

    public FakePasscodeAccountActionsActivity(AccountActions actions, FakePasscode fakePasscode) {
        super();
        this.actions = actions;
        this.fakePasscode = fakePasscode;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
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

        TLRPC.User user = UserConfig.getInstance(actions.getAccountNum()).getCurrentUser();
        String activityTitle = "";
        if (user.first_name != null && user.last_name != null) {
            activityTitle = user.first_name + " " + user.last_name;
        } else if (user.first_name != null) {
            activityTitle = user.first_name;
        } else if (user.last_name != null) {
            activityTitle = user.last_name;
        }
        actionBar.setTitle(activityTitle);
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
                if (position == changePhoneRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.formatString(R.string.CannotEnableSettingDescription, LocaleController.getString(R.string.ReplaceOriginalPasscode)));
                    builder.setTitle(LocaleController.getString(R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                } else if (position == sessionsToHideRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.formatString(R.string.CannotEnableSettingDescription, LocaleController.getString(R.string.ReplaceOriginalPasscode)));
                    builder.setTitle(LocaleController.getString(R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                } else if (position == logOutRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString(R.string.CannotHideAllAccountsLogout));
                    builder.setTitle(LocaleController.getString(R.string.CannotLogOutAccount));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                } else if (position == hideAccountRow) {
                    NotificationsCheckCell cell = (NotificationsCheckCell) view;
                    String title;
                    String message;
                    if (cell.isChecked()) {
                        title = LocaleController.getString(R.string.CannotRemoveHiding);
                        message = String.format(LocaleController.getString(R.string.CannotShowManyAccounts),
                                UserConfig.getFakePasscodeMaxAccountCount());
                    } else {
                        title = LocaleController.getString(R.string.CannotHideAccount);
                        if (fakePasscode.replaceOriginalPasscode) {
                            message = LocaleController.formatString(R.string.CannotEnableSettingDescription, LocaleController.getString(R.string.ReplaceOriginalPasscode));
                        } else if (UserConfig.getActivatedAccountsCount() == 1) {
                            message = LocaleController.getString(R.string.CannotHideSingleAccount);
                        } else {
                            message = LocaleController.getString(R.string.CannotHideAllAccounts);
                        }
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(message);
                    builder.setTitle(title);
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                }
                return;
            }
            if (position == changeTelegramMessageRow) {
                presentFragment(new FakePasscodeTelegramMessagesActivity(actions.getTelegramMessageAction(), actions.getAccountNum()));
            } else if (position == changePhoneRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                template.title = LocaleController.getString(R.string.FakePhoneNumber);
                template.addPhoneEditTemplate(actions.getFakePhone().isEmpty() ? "" : "+" + actions.getFakePhone(), LocaleController.getString(R.string.FakePhoneNumber), true);
                template.positiveListener = views -> {
                    actions.setFakePhone(((EditTextCaption)views.get(0)).getText().toString()
                            .replace("+", "").replace("-", "").replace(" ", ""));
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    String value = actions.getFakePhone().isEmpty() ? LocaleController.getString(R.string.Disabled) : PhoneFormat.getInstance().format("+" + actions.getFakePhone());
                    cell.setTextAndValue(LocaleController.getString(R.string.ActivationMessage), value, false);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                };
                template.negativeListener = (dlg, whichButton) -> {
                    actions.removeFakePhone();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(LocaleController.getString(R.string.ActivationMessage), LocaleController.getString(R.string.Disabled), false);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == changeChatsToRemoveRow) {
                presentFragment(new RemoveChatsFragment(fakePasscode, actions.getRemoveChatsAction(), actions.getAccountNum()));
            } else if (position == deleteAllContactsRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleDeleteContactsAction();
                cell.setChecked(actions.isDeleteContacts());
            } else if (position == deleteAllStickersRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleDeleteStickersAction();
                cell.setChecked(actions.isDeleteStickers());
            } else if (position == clearSearchHistoryRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleClearSearchHistoryAction();
                cell.setChecked(actions.isClearSearchHistory());
            } else if (position == clearBlackListRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleClearBlackListAction();
                cell.setChecked(actions.isClearBlackList());
            } else if (position == clearSavedChannelsRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleClearSavedChannelsAction();
                cell.setChecked(actions.isClearSavedChannels());
            } else if (position == clearDraftsRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleClearDraftsAction();
                cell.setChecked(actions.isClearDraftsAction());
            } else if (position == logOutRow) {
                TextCheckCell cell = (TextCheckCell) view;
                actions.toggleLogOutAction();
                cell.setChecked(actions.isLogOut());
                if (!actions.isLogOut()) {
                    int targetHideCount = UserConfig.getActivatedAccountsCount() - UserConfig.getFakePasscodeMaxAccountCount();
                    if (!actions.isHideAccount() && fakePasscode.getHideOrLogOutCount() < targetHideCount) {
                        actions.toggleHideAccountAction();
                    }
                } else {
                    if (actions.isHideAccount()) {
                        actions.toggleHideAccountAction();
                    }
                }
                if (listAdapter != null) {
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                }
                ContactsController.getInstance(actions.getAccountNum()).checkAppAccount();
                NotificationsController.getInstance(actions.getAccountNum()).cleanupSystemSettings();
            } else if (position == hideAccountRow) {
                NotificationsCheckCell cell = (NotificationsCheckCell) view;
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    actions.toggleHideAccountAction();
                    cell.setChecked(actions.isHideAccount());
                    final int maxAccountHidings = UserConfig.MAX_ACCOUNT_COUNT - UserConfig.getFakePasscodeMaxAccountCount();
                    if (actions.isHideAccount() && fakePasscode.getHideAccountCount() > maxAccountHidings) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        String message = String.format(LocaleController.getString(R.string.TooManyAccountsHiddenDescription),
                                maxAccountHidings);
                        builder.setMessage(message);
                        builder.setTitle(LocaleController.getString(R.string.TooManyAccountsHiddenTitle));
                        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                        showDialog(builder.create());
                    }
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(hideAccountRow);
                    }
                    ContactsController.getInstance(actions.getAccountNum()).checkAppAccount();
                    NotificationsController.getInstance(actions.getAccountNum()).cleanupSystemSettings();
                } else {
                    presentFragment(new AccountHidingSettingsActivity(actions));
                }
            } else if (position == sessionsToTerminateRow) {
                presentFragment(new SessionsToTerminateActivity(actions));
            } else if (position == sessionsToHideRow) {
                presentFragment(new SessionsToHideActivity(actions));
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowCount = 0;

        changeTelegramMessageRow = rowCount++;
        messagesDetailRow = rowCount++;

        changePhoneRow = rowCount++;
        phoneDetailRow = rowCount++;

        changeChatsToRemoveRow = rowCount++;
        changeChatsToRemoveDetailRow = rowCount++;

        sessionsToTerminateRow = rowCount++;
        sessionsToHideRow = rowCount++;
        sessionsSettingsDetailRow = rowCount++;

        deleteAllContactsRow = rowCount++;
        deleteAllStickersRow = rowCount++;
        clearSearchHistoryRow = rowCount++;
        clearBlackListRow = rowCount++;
        clearSavedChannelsRow = rowCount++;
        clearDraftsRow = rowCount++;
        logOutRow = rowCount++;
        if (!actions.isLogOut()) {
            hideAccountRow = rowCount++;
        } else {
            hideAccountRow = -1;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != messagesDetailRow && position != phoneDetailRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == deleteAllContactsRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.SyncContactsDelete),
                                actions.isDeleteContacts(), true);
                    } else if (position == deleteAllStickersRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.DeleteStickers),
                                actions.isDeleteStickers(), true);
                    } else if (position == clearSearchHistoryRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearSearchAlertTitle),
                                actions.isClearSearchHistory(), true);
                    } else if (position == clearBlackListRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearBlackList),
                                actions.isClearBlackList(), true);
                    } else if (position == clearSavedChannelsRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearSavedChannels),
                                actions.isClearSavedChannels(), true);
                    } else if (position == clearDraftsRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearDrafts),
                                actions.isClearDraftsAction(), true);
                    } else if (position == logOutRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.LogOutOnFakeLogin),
                                actions.isLogOut(), false);
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changeTelegramMessageRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.SendTelegramMessages),
                                String.valueOf(actions.getTelegramMessageAction().entries.size()), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == changePhoneRow) {
                        String value;
                        if (actions.getFakePhone().isEmpty()) {
                            value = LocaleController.getString(R.string.Disabled);
                        } else {
                            value = PhoneFormat.getInstance().format("+" + actions.getFakePhone());
                        }
                        textCell.setTextAndValue(LocaleController.getString(R.string.FakePhoneNumber), value, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == changeChatsToRemoveRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.ChatsToRemove),
                                String.valueOf(actions.getChatsToRemoveCount()), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }  else if (position == sessionsToTerminateRow) {
                        TerminateOtherSessionsAction action = actions.getTerminateOtherSessionsAction();
                        textCell.setTextAndValue(LocaleController.getString(R.string.SessionsToTerminate),
                                getSessionsLabel(action.getMode(), action.getSessions().size()), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }  else if (position == sessionsToHideRow) {
                        CheckedSessions action = actions.getSessionsToHide();
                        textCell.setTextAndValue(LocaleController.getString(R.string.SessionsToHide),
                                getSessionsLabel(action.getMode(), action.getSessions().size()), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == messagesDetailRow) {
                        cell.setText(LocaleController.getString(R.string.FakePasscodeTelegramMessageInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == phoneDetailRow) {
                        cell.setText(LocaleController.getString(R.string.FakePhoneNumberInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == changeChatsToRemoveDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ChatsToRemoveInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == sessionsSettingsDetailRow) {
                        cell.setText(LocaleController.getString(R.string.SessionsToHideSettingsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    NotificationsCheckCell textCell = (NotificationsCheckCell) holder.itemView;
                    if (position == hideAccountRow) {
                        String value;
                        if (actions.getHideAccountAction() != null) {
                            if (actions.getHideAccountAction().strictHiding) {
                                value = LocaleController.getString(R.string.StrictHiding);
                            } else {
                                value = LocaleController.getString(R.string.PopupEnabled);
                            }
                        } else {
                            value = LocaleController.getString(R.string.PopupDisabled);
                        }
                        textCell.setTextAndValueAndCheck(LocaleController.getString(R.string.HideAccount), value,
                                actions.isHideAccount(), false);
                    }
                }
            }
        }

        private String getSessionsLabel(int mode, int sessionsCount) {
            if (mode == 1) {
                if (sessionsCount > 0) {
                    return LocaleController.formatString(R.string.AllExceptCount, sessionsCount);
                } else {
                    return LocaleController.getString(R.string.FilterAllChatsShort);
                }
            } else {
                return String.valueOf(sessionsCount);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) {
                TextCheckCell textCell = (TextCheckCell) holder.itemView;
                if (holder.getAdapterPosition() == logOutRow) {
                    int accountCount = UserConfig.getActivatedAccountsCount();
                    boolean enabled = actions.isLogOut()
                            || fakePasscode.getHideAccountCount() == 0
                            || actions.isHideAccount()
                            || !actions.isLogOut() && (fakePasscode.getHideOrLogOutCount() < accountCount - 1);
                    textCell.setEnabled(enabled, null);
                } else {
                    textCell.setEnabled(isEnabled(holder), null);
                }
            } else if (holder.getItemViewType() == 1) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (holder.getAdapterPosition() == changePhoneRow) {
                    textCell.setEnabled(!fakePasscode.replaceOriginalPasscode, null);
                } else if (holder.getAdapterPosition() == sessionsToHideRow) {
                    textCell.setEnabled(!fakePasscode.replaceOriginalPasscode, null);
                } else {
                    textCell.setEnabled(isEnabled(holder), null);
                }
            } else if (holder.getItemViewType() == 3) {
                NotificationsCheckCell textCell = (NotificationsCheckCell) holder.itemView;
                if (holder.getAdapterPosition() == hideAccountRow) {
                    int hiddenAccountCount = fakePasscode.getHideOrLogOutCount();
                    int accountCount = UserConfig.getActivatedAccountsCount();
                    boolean enabled;
                    if (fakePasscode.replaceOriginalPasscode) {
                        enabled = false;
                    } else if (actions.isHideAccount()) {
                        enabled = accountCount - hiddenAccountCount < UserConfig.getFakePasscodeMaxAccountCount();
                    } else {
                        enabled = hiddenAccountCount < accountCount - 1;
                    }
                    textCell.setEnabled(enabled);
                } else {
                    textCell.setEnabled(isEnabled(holder));
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == deleteAllContactsRow || position == deleteAllStickersRow || position == clearSearchHistoryRow
                    || position == clearBlackListRow || position == clearSavedChannelsRow
                    || position == clearDraftsRow || position == logOutRow) {
                return 0;
            } else if (position == changeChatsToRemoveRow || position == changePhoneRow ||  position == changeTelegramMessageRow
                    || position == sessionsToTerminateRow || position == sessionsToHideRow) {
                return 1;
            } else if (position == messagesDetailRow || position == phoneDetailRow
                    || position == changeChatsToRemoveDetailRow || position == sessionsSettingsDetailRow) {
                return 2;
            } else if (position == hideAccountRow) {
                return 3;
            }
            return 0;
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
