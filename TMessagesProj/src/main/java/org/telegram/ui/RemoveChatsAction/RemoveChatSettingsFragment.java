package org.telegram.ui.RemoveChatsAction;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxThreeStateCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SimpleRadioButtonCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CheckBoxSquareThreeState;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogBuilder.DialogCheckBox;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;
import org.telegram.ui.RemoveChatsAction.items.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class RemoveChatSettingsFragment extends BaseFragment {
    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private final FakePasscode fakePasscode;
    private final RemoveChatsAction action;
    int accountNum;
    private final Collection<Item> items;
    private final List<RemoveChatsAction.RemoveChatEntry> removeChatEntries = new ArrayList<>();
    private final boolean isNew;
    private boolean changed;

    private int rowCount;

    private int deleteDialogRow;
    private int deleteDialogDetailsEmptyRow;
    private int deleteFromCompanionRow;
    private int deleteFromCompanionDetailsRow;
    private int deleteNewMessagesRow;
    private int deleteNewMessagesDetailsRow;
    private int deleteAllMyMessagesRow;
    private int deleteAllMyMessagesDetailsRow;
    private int hideDialogRow;
    private int hideDialogDetailsRow;
    private int strictHidingRow;
    private int strictHidingDetailsRow;

    private static final int done_button = 1;

    public RemoveChatSettingsFragment(FakePasscode fakePasscode, RemoveChatsAction action, Collection<Item> items, int accountNum) {
        super();
        this.fakePasscode = fakePasscode;
        this.action = action;
        this.items = items;
        this.isNew = false;
        this.accountNum = accountNum;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        initEntries();
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
                    if (isNew || changed) {
                        confirmExit();
                    } else {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("FakePasscodeRemoveDialogSettingsTitle", R.string.FakePasscodeRemoveDialogSettingsTitle));
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(done_button, LocaleController.getString("Save", R.string.Save).toUpperCase());

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
        listView.setOnItemClickListener((view, position) -> {
            if (!view.isEnabled()) {
                if (position == hideDialogRow) {
                    String title;
                    String message;
                    title = LocaleController.getString(R.string.CannotHideDialog);
                    if (fakePasscode.replaceOriginalPasscode) {
                        message = LocaleController.formatString(R.string.CannotEnableSettingDescription, LocaleController.getString(R.string.ReplaceOriginalPasscode));
                    } else {
                        message = LocaleController.getString(R.string.CannotHideSavedMessages);
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

            if (position == deleteDialogRow) {
                if (hasHideDialog()) {
                    changed = true;
                }
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.isExitFromChat = true;
                    entry.strictHiding = false;
                }
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (position == deleteFromCompanionRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.isDeleteFromCompanion = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == deleteNewMessagesRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.isDeleteNewMessages = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == deleteAllMyMessagesRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.isClearChat = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            } else if (position == hideDialogRow) {
                if (SharedConfig.showHideDialogIsNotSafeWarning) {
                    showHideDialogIsNotSafeWarning();
                }

                if (hasDeleteDialog()) {
                    changed = true;
                }
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.isExitFromChat = false;
                    entry.isClearChat = false;
                    entry.isDeleteFromCompanion = false;
                    entry.isDeleteNewMessages = false;
                }
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (position == strictHidingRow) {
                changed = true;
                CheckBoxThreeStateCell checkBox = (CheckBoxThreeStateCell) view;
                boolean checked = checkBox.getState() != CheckBoxSquareThreeState.State.UNCHECKED;
                for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                    entry.strictHiding = !checked;
                }
                checkBox.setState(checked ? CheckBoxSquareThreeState.State.UNCHECKED : CheckBoxSquareThreeState.State.CHECKED, true);
            }
        });

        return fragmentView;
    }

    private void showHideDialogIsNotSafeWarning() {
        DialogTemplate template = new DialogTemplate();
        template.type = DialogType.OK;
        template.title = LocaleController.getString("Warning", R.string.Warning);
        template.message = LocaleController.getString("HideDialogIsNotSafeWarningMessage", R.string.HideDialogIsNotSafeWarningMessage);
        template.addCheckboxTemplate(false, LocaleController.getString("DoNotShowAgain", R.string.DoNotShowAgain));
        template.positiveListener = views -> {
            boolean isNotShowAgain = !((DialogCheckBox) views.get(0)).isChecked();
            if (SharedConfig.showHideDialogIsNotSafeWarning != isNotShowAgain) {
                SharedConfig.showHideDialogIsNotSafeWarning = isNotShowAgain;
                SharedConfig.saveConfig();
            }
        };
        showDialog(FakePasscodeDialogBuilder.build(getParentActivity(), template));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (isNew || changed) {
            confirmExit();
        } else {
            finishFragment();
        }
        return false;
    }

    private void updateRows() {
        rowCount = 0;

        deleteDialogRow = -1;
        deleteFromCompanionRow = -1;
        deleteFromCompanionDetailsRow = -1;
        deleteNewMessagesRow = -1;
        deleteNewMessagesDetailsRow = -1;
        deleteAllMyMessagesRow = -1;
        deleteAllMyMessagesDetailsRow = -1;
        deleteDialogDetailsEmptyRow = -1;
        hideDialogRow = -1;
        hideDialogDetailsRow = -1;
        strictHidingRow = -1;
        strictHidingDetailsRow = -1;

        deleteDialogRow = rowCount++;

        if (hasUsers() || hasEncryptedChats()) {
            deleteFromCompanionRow = rowCount++;
            deleteFromCompanionDetailsRow = rowCount++;
        }

        if (hasUsers()) {
            deleteNewMessagesRow = rowCount++;
            deleteNewMessagesDetailsRow = rowCount++;
        }

        if (hasChats()) {
            deleteAllMyMessagesRow = rowCount++;
            deleteAllMyMessagesDetailsRow = rowCount++;
        }

        if (deleteDialogRow == rowCount - 1) {
            deleteDialogDetailsEmptyRow = rowCount++;
        }

        if (!hasOnlySavedMessages()) {
            hideDialogRow = rowCount++;
            hideDialogDetailsRow = rowCount++;

            strictHidingRow = rowCount++;
            strictHidingDetailsRow = rowCount++;
        }
    }

    private void initEntries() {
        if (items == null) {
            return;
        }

        for (Item item : items) {
            long id = item.getId();
            if (action.contains(id)) {
                removeChatEntries.add(action.get(id).copy());
            } else {
                String title = item.getDisplayName().toString();
                boolean isExitFromChat = !fakePasscode.passwordlessMode || id == getUserConfig().clientUserId;
                removeChatEntries.add(new RemoveChatsAction.RemoveChatEntry(id, title, isExitFromChat));
            }
        }
    }

    private void processDone() {
        if (hasIndeterminateStates()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            String buttonText;
            builder.setMessage(LocaleController.getString("RemoveDialogCantSaveDetails", R.string.RemoveDialogCantSaveDetails));
            builder.setTitle(LocaleController.getString("RemoveDialogCantSaveTitle", R.string.RemoveDialogCantSaveTitle));
            buttonText = LocaleController.getString("OK", R.string.OK);
            builder.setPositiveButton(buttonText, null);
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
        } else {
            for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
                action.remove(entry.chatId);
                action.add(entry);
                SharedConfig.saveConfig();
            }
            finishFragment();
        }
    }

    private boolean hasUsers() {
        return removeChatEntries.stream().map(e -> (long)e.chatId).anyMatch(DialogObject::isUserDialog);
    }

    private boolean hasEncryptedChats() {
        return removeChatEntries.stream().map(e -> (long)e.chatId).anyMatch(DialogObject::isEncryptedDialog);
    }

    private boolean hasChats() {
        return removeChatEntries.stream().map(e -> (long)e.chatId).anyMatch(did -> {
            if (!DialogObject.isChatDialog(did)) {
                return false;
            }
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            return !ChatObject.isChannel(chat) || chat.megagroup;
        });
    }

    private boolean hasSavedMessages() {
        return removeChatEntries.stream().anyMatch(e -> e.chatId == getUserConfig().clientUserId);
    }

    private boolean hasOnlySavedMessages() {
        return removeChatEntries.size() == 1 && removeChatEntries.get(0).chatId == getUserConfig().clientUserId;
    }

    private boolean hasDeleteDialog() {
        for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
            if (entry.isExitFromChat) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHideDialog() {
        for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
            if (!entry.isExitFromChat) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIndeterminateStates() {
        if (!hasDeleteDialog()) {
            return false;
        }
        if (hasHideDialog()) {
            return true;
        }
        return getDeleteFromCompanionState() == CheckBoxSquareThreeState.State.INDETERMINATE
                || getDeleteNewMessagesState() == CheckBoxSquareThreeState.State.INDETERMINATE
                || getDeleteAllMyMessagesState() == CheckBoxSquareThreeState.State.INDETERMINATE
                || getStrictHidingState() == CheckBoxSquareThreeState.State.INDETERMINATE;
    }

    private void confirmExit() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        String buttonText;
        builder.setTitle(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
        builder.setMessage(LocaleController.getString("PhotoEditorDiscardAlert", R.string.PhotoEditorDiscardAlert));
        buttonText = LocaleController.getString("PassportDiscard", R.string.PassportDiscard);
        builder.setPositiveButton(buttonText, (dialogInterface, i) -> finishFragment());
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_color_red));
        }
    }

    @Override
    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    private CheckBoxSquareThreeState.State getState(Function<RemoveChatsAction.RemoveChatEntry, Boolean> getValue) {
        CheckBoxSquareThreeState.State state = null;
        for (RemoveChatsAction.RemoveChatEntry entry : removeChatEntries) {
            CheckBoxSquareThreeState.State newState = getValue.apply(entry)
                    ? CheckBoxSquareThreeState.State.CHECKED
                    : CheckBoxSquareThreeState.State.UNCHECKED;
            if (state == null) {
                state = newState;
            } else if (state != newState) {
                return CheckBoxSquareThreeState.State.INDETERMINATE;
            }
        }
        return state;
    }

    private CheckBoxSquareThreeState.State getDeleteFromCompanionState() {
        return getState(e -> e.isDeleteFromCompanion);
    }

    private CheckBoxSquareThreeState.State getDeleteNewMessagesState() {
        return getState(e -> e.isDeleteNewMessages);
    }

    private CheckBoxSquareThreeState.State getDeleteAllMyMessagesState() {
        return getState(e -> e.isClearChat);
    }

    private CheckBoxSquareThreeState.State getStrictHidingState() {
        return getState(e -> e.strictHiding);
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
            if (position == deleteFromCompanionRow || position == deleteNewMessagesRow
                    || position == deleteAllMyMessagesRow) {
                return hasDeleteDialog();
            } else if (position == hideDialogRow) {
                return !hasSavedMessages() && !fakePasscode.replaceOriginalPasscode;
            } else if (position == strictHidingRow) {
                return hasHideDialog();
            }
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
                case 0:
                    view = new SimpleRadioButtonCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new CheckBoxThreeStateCell(mContext, 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                default:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    SimpleRadioButtonCell radioButtonCell = (SimpleRadioButtonCell) holder.itemView;
                    if (position == deleteDialogRow) {
                        radioButtonCell.setTextAndValue(LocaleController.getString("Delete", R.string.Delete), false, hasDeleteDialog());
                    } else if (position == hideDialogRow) {
                        radioButtonCell.setTextAndValue(LocaleController.getString("Hide", R.string.Hide), false, hasHideDialog());
                    }
                    break;
                }
                case 1: {
                    CheckBoxThreeStateCell checkBoxCell = (CheckBoxThreeStateCell) holder.itemView;
                    if (position == deleteFromCompanionRow) {
                        String title = LocaleController.getString("DeleteFromCompanion", R.string.DeleteFromCompanion);
                        checkBoxCell.setText(title, "", getDeleteFromCompanionState(), false);
                    } else if (position == deleteNewMessagesRow) {
                        String title = LocaleController.getString("DeleteNewMessages", R.string.DeleteNewMessages);
                        checkBoxCell.setText(title, "", getDeleteNewMessagesState(), false);
                    } else if (position == deleteAllMyMessagesRow) {
                        String title = LocaleController.getString("DeleteAllMyMessages", R.string.DeleteAllMyMessages);
                        checkBoxCell.setText(title, "", getDeleteAllMyMessagesState(), false);
                    } else if (position == strictHidingRow) {
                        String title = LocaleController.getString(R.string.StrictHiding);
                        checkBoxCell.setText(title, "", getStrictHidingState(), false);
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == deleteFromCompanionDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteFromCompanionDetails", R.string.DeleteFromCompanionDetails));
                    } else if (position == deleteNewMessagesDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteNewMessagesDetails", R.string.DeleteNewMessagesDetails));
                    } else if (position == deleteAllMyMessagesDetailsRow) {
                        textCell.setText(LocaleController.getString("DeleteAllMyMessagesDetails", R.string.DeleteAllMyMessagesDetails));
                    } else if (position == hideDialogDetailsRow) {
                        textCell.setText(LocaleController.getString("HideDialogDetails", R.string.HideDialogDetails));
                    } else if (position == strictHidingDetailsRow) {
                        textCell.setText(LocaleController.getString(R.string.StrictHidingDialogDescription));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == deleteDialogRow || position == hideDialogRow) {
                return 0;
            } else if (position == deleteFromCompanionRow || position == deleteNewMessagesRow
                    || position == deleteAllMyMessagesRow || position == strictHidingRow) {
                return 1;
            } else if (position == deleteFromCompanionDetailsRow || position == deleteNewMessagesDetailsRow
                    || position == deleteAllMyMessagesDetailsRow || position == hideDialogDetailsRow
                    || position == strictHidingDetailsRow) {
                return 2;
            } else if (position == deleteDialogDetailsEmptyRow) {
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
