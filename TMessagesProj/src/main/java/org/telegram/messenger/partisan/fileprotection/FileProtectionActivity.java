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
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileProtectionActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int worksWithFakePasscodeRow;
    private int worksWithFakePasscodeDelimiterRow;
    private int firstAccountRow;
    private int lastAccountRow;
    private int rowCount;

    private final List<FileProtectionAccountCellInfo> accounts = new ArrayList<>();

    private static final int done_button = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.FileProtection));
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(done_button, LocaleController.getString("Save", R.string.Save).toUpperCase());
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
                    if (isChanged()) {
                        processDone();
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAnimateEmptyView(true, 0);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == worksWithFakePasscodeRow) {
                SharedConfig.toggleFileProtectionWorksWhenFakePasscodeActivated();
                TextCheckCell textCell = (TextCheckCell) view;
                textCell.setChecked(SharedConfig.fileProtectionWorksWhenFakePasscodeActivated);
            }
            if (firstAccountRow <= position && position <= lastAccountRow) {
                CheckBoxUserCell userCell = ((CheckBoxUserCell) view);
                FileProtectionAccountCellInfo cellInfo = accounts.get(position - firstAccountRow);
                cellInfo.fileProtectionEnabled = !cellInfo.fileProtectionEnabled;
                listAdapter.notifyItemChanged(position);
                userCell.setChecked(cellInfo.fileProtectionEnabled, true);
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

        worksWithFakePasscodeRow = rowCount++;
        worksWithFakePasscodeDelimiterRow = rowCount++;
        firstAccountRow = rowCount;
        accounts.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(new FileProtectionAccountCellInfo(a));
                lastAccountRow = rowCount++;
            }
        }
        Collections.sort(accounts, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1.accountNum).loginTime;
            long l2 = UserConfig.getInstance(o2.accountNum).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });
    }

    private boolean isChanged() {
        for (FileProtectionAccountCellInfo cellInfo : accounts) {
            if (cellInfo.getUserConfig().fileProtectionEnabled != cellInfo.fileProtectionEnabled) {
                return true;
            }
        }
        return false;
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

    private void processDone() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(LocaleController.getString(R.string.ApplicationWillBeRestarted));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialogInterface, i) -> {
            Map<Integer, Boolean> map = new HashMap<>();
            for (FileProtectionAccountCellInfo cellInfo : accounts) {
                map.put(cellInfo.accountNum, cellInfo.fileProtectionEnabled);
            }
            new FileProtectionSwitcher(this).changeForMultipleAccounts(map);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
    }

    private static class FileProtectionAccountCellInfo {
        public int accountNum;
        public boolean fileProtectionEnabled;

        public FileProtectionAccountCellInfo(int accountNum) {
            this.accountNum = accountNum;
            this.fileProtectionEnabled = SharedConfig.fileProtectionForAllAccountsEnabled
                    || getUserConfig().fileProtectionEnabled;
        }

        public UserConfig getUserConfig() {
            return UserConfig.getInstance(accountNum);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
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
                    userCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    userCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                    FileProtectionAccountCellInfo cellInfo = accounts.get(position - firstAccountRow);
                    userCell.setUser(cellInfo.getUserConfig().getCurrentUser(), cellInfo.fileProtectionEnabled, true);
                    break;
                }
                case 1: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == worksWithFakePasscodeRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.WorksWithFakePasscodes), SharedConfig.fileProtectionWorksWhenFakePasscodeActivated, true);
                    }
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
            if (position == worksWithFakePasscodeRow) {
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_color_red));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }
}
