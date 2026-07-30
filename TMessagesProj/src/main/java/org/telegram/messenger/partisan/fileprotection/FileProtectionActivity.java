package org.telegram.messenger.partisan.fileprotection;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class FileProtectionActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int storeDataInMemoryOnlyRow;
    private int storeChatsInMemoryOnlyRow;
    private int encryptDatabaseRow;
    private int encryptAuthTokenRow;
    private int worksWithFakePasscodeRow;
    private int worksWithFakePasscodeDelimiterRow;
    private int firstAccountRow;
    private int lastAccountRow;
    private int rowCount;

    private final List<FileProtectionAccountInfo> accounts = new ArrayList<>();
    private boolean storeDataInMemoryOnly;
    private boolean storeChatsInMemoryOnly;
    private boolean encryptDatabase;
    private boolean encryptAuthToken;
    private boolean fileProtectionWorksWhenFakePasscodeActivated;

    private static final int done_button = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        storeDataInMemoryOnly = FileProtectionSettings.storeDataInMemoryOnly.get().orElse(true);
        storeChatsInMemoryOnly = FileProtectionSettings.storeChatsInMemoryOnly.get().orElse(true);
        encryptDatabase = FileProtectionSettings.encryptDatabase.get().orElse(true);
        encryptAuthToken = FileProtectionSettings.encryptAuthToken.get().orElse(true);
        fileProtectionWorksWhenFakePasscodeActivated = FileProtectionSettings.fileProtectionWorksWhenFakePasscodeActivated.get().orElse(true);
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.FileProtection));
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(done_button, LocaleController.getString(R.string.Save).toUpperCase());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (isChanged()) {
                        confirmExit();
                    } else {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAnimateEmptyView(true, 0);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == storeDataInMemoryOnlyRow) {
                storeDataInMemoryOnly = !storeDataInMemoryOnly;
                ((TextCheckCell) view).setChecked(storeDataInMemoryOnly);
                listAdapter.notifyItemChanged(storeChatsInMemoryOnlyRow);
            }
            if (position == storeChatsInMemoryOnlyRow && storeDataInMemoryOnly) {
                storeChatsInMemoryOnly = !storeChatsInMemoryOnly;
                ((TextCheckCell) view).setChecked(storeChatsInMemoryOnly);
            }
            if (position == encryptDatabaseRow) {
                encryptDatabase = !encryptDatabase;
                ((TextCheckCell) view).setChecked(encryptDatabase);
            }
            if (position == encryptAuthTokenRow) {
                encryptAuthToken = !encryptAuthToken;
                ((TextCheckCell) view).setChecked(encryptAuthToken);
            }
            if (position == worksWithFakePasscodeRow) {
                fileProtectionWorksWhenFakePasscodeActivated = !fileProtectionWorksWhenFakePasscodeActivated;
                TextCheckCell textCell = (TextCheckCell) view;
                textCell.setChecked(fileProtectionWorksWhenFakePasscodeActivated);
            }
            if (firstAccountRow <= position && position <= lastAccountRow) {
                CheckBoxUserCell userCell = ((CheckBoxUserCell) view);
                FileProtectionAccountInfo accountInfo = accounts.get(position - firstAccountRow);
                accountInfo.fileProtectionEnabled = !accountInfo.fileProtectionEnabled;
                listAdapter.notifyItemChanged(position);
                userCell.setChecked(accountInfo.fileProtectionEnabled, true);
            }
        });

        updateRows();
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

        storeDataInMemoryOnlyRow = rowCount++;
        storeChatsInMemoryOnlyRow = rowCount++;
        encryptDatabaseRow = rowCount++;
        encryptAuthTokenRow = rowCount++;
        worksWithFakePasscodeRow = rowCount++;
        worksWithFakePasscodeDelimiterRow = rowCount++;
        firstAccountRow = rowCount;
        accounts.clear();
        for (int account : Utils.getActivatedAccountsSortedByLoginTime()) {
            accounts.add(new FileProtectionAccountInfo(account));
            lastAccountRow = rowCount++;
        }
    }

    private boolean isChanged() {
        return FileProtectionSwitcher.fileProtectedAccountsChanged(accounts)
                || storeDataInMemoryOnlyChanged()
                || storeChatsInMemoryOnlyChanged()
                || encryptDatabaseChanged()
                || encryptAuthTokenChanged()
                || fileProtectionWorksWhenFakePasscodeActivatedChanged();
    }

    private boolean storeDataInMemoryOnlyChanged() {
        return storeDataInMemoryOnly != FileProtectionSettings.storeDataInMemoryOnly.get().orElse(true);
    }

    private boolean storeChatsInMemoryOnlyChanged() {
        return storeChatsInMemoryOnly != FileProtectionSettings.storeChatsInMemoryOnly.get().orElse(true);
    }

    private boolean encryptDatabaseChanged() {
        return encryptDatabase != FileProtectionSettings.encryptDatabase.get().orElse(true);
    }

    private boolean encryptAuthTokenChanged() {
        return encryptAuthToken != FileProtectionSettings.encryptAuthToken.get().orElse(true);
    }

    private boolean fileProtectionWorksWhenFakePasscodeActivatedChanged() {
        return fileProtectionWorksWhenFakePasscodeActivated != FileProtectionSettings.fileProtectionWorksWhenFakePasscodeActivated.get().orElse(true);
    }

    private void confirmExit() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        String buttonText;
        builder.setTitle(LocaleController.getString(R.string.DiscardChanges));
        builder.setMessage(LocaleController.getString(R.string.PhotoEditorDiscardAlert));
        buttonText = LocaleController.getString(R.string.PassportDiscard);
        builder.setPositiveButton(buttonText, (dialogInterface, i) -> finishFragment());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_color_red));
        }
    }

    private boolean switchingNeeded() {
        return FileProtectionSwitcher.fileProtectedAccountsChanged(accounts)
                || storeDataInMemoryOnlyChanged()
                || storeChatsInMemoryOnlyChanged()
                || encryptDatabaseChanged()
                || encryptAuthTokenChanged();
    }

    private void processDone() {
        if (!switchingNeeded()) {
            if (fileProtectionWorksWhenFakePasscodeActivatedChanged()) {
                FileProtectionSettings.fileProtectionWorksWhenFakePasscodeActivated.toggle();
            }
            finishFragment();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(LocaleController.getString(R.string.ApplicationWillBeRestarted));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> {
            if (fileProtectionWorksWhenFakePasscodeActivatedChanged()) {
                FileProtectionSettings.fileProtectionWorksWhenFakePasscodeActivated.toggle();
            }
            if (switchingNeeded()) {
                new FileProtectionSwitcher(this).apply(accounts, storeDataInMemoryOnly, storeChatsInMemoryOnly, encryptDatabase, encryptAuthToken);
            } else {
                finishFragment();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == storeChatsInMemoryOnlyRow) {
                return storeDataInMemoryOnly;
            }
            return position != worksWithFakePasscodeDelimiterRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    CheckBoxUserCell userCell = new CheckBoxUserCell(mContext, false);
                    view = userCell;
                    userCell.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    CheckBoxUserCell userCell = (CheckBoxUserCell) holder.itemView;
                    FileProtectionAccountInfo accountInfo = accounts.get(position - firstAccountRow);
                    userCell.setUser(accountInfo.getUserConfig().getCurrentUser(), accountInfo.fileProtectionEnabled, true);
                    break;
                }
                case 1: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == storeDataInMemoryOnlyRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.FileProtectionStoreDataInMemoryOnly), storeDataInMemoryOnly, true);
                    } else if (position == storeChatsInMemoryOnlyRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.FileProtectionStoreChatsInMemoryOnly), storeChatsInMemoryOnly, true);
                    } else if (position == encryptDatabaseRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.FileProtectionEncryptDatabase), encryptDatabase, true);
                    } else if (position == encryptAuthTokenRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.FileProtectionEncryptAuthToken), encryptAuthToken, true);
                    } else if (position == worksWithFakePasscodeRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.WorksWithFakePasscodes), fileProtectionWorksWhenFakePasscodeActivated, true);
                    }
                    textCell.setEnabled(position != storeChatsInMemoryOnlyRow || storeDataInMemoryOnly, null);
                    break;
                }
                case 2: {
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == storeDataInMemoryOnlyRow || position == storeChatsInMemoryOnlyRow || position == encryptDatabaseRow || position == encryptAuthTokenRow || position == worksWithFakePasscodeRow) {
                return 1;
            } else if (position == worksWithFakePasscodeDelimiterRow) {
                return 2;
            } if (firstAccountRow <= position && position <= lastAccountRow) {
                return 0;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{CheckBoxUserCell.class, TextSettingsCell.class, HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckBoxUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckBoxUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckBoxUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_color_red));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }
}
