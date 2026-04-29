package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FakePasscodeRemovePasscodesActivity extends BaseFragment implements AlertsCreator.CheckabeSettingModeAlertDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private final FakePasscode currentPasscode;
    private final List<FakePasscode> fakePasscodes;
    private List<FakePasscode> selectedPasscodes;

    private int modeSectionRow;
    private int checkAllRow;
    private int fakePasscodesSectionRow;
    private int fakePasscodesStartRow;
    private int rowCount;

    FakePasscodeRemovePasscodesActivity(@NonNull FakePasscode currentPasscode) {
        this.currentPasscode = currentPasscode;
        fakePasscodes = SharedConfig.fakePasscodes
                .stream()
                .filter(code -> !code.uuid.equals(currentPasscode.uuid))
                .collect(Collectors.toList());
        List<UUID> selectedPasscodes = currentPasscode.deletePasscodesAfterActivation.getSelected();
        this.selectedPasscodes = fakePasscodes
                .stream()
                .filter(code -> selectedPasscodes.contains(code.uuid))
                .collect(Collectors.toList());
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
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
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
            if (fakePasscodesStartRow != -1 && position >= fakePasscodesStartRow) {
                TextCheckCell checkableSessionCell = ((TextCheckCell) view);
                boolean isChecked = !checkableSessionCell.isChecked();
                if (isChecked) {
                    selectedPasscodes.add(fakePasscodes.get(position - fakePasscodesStartRow));
                } else {
                    selectedPasscodes.remove(fakePasscodes.get(position - fakePasscodesStartRow));
                }
                saveCheckedPasscodes(selectedPasscodes);
                listAdapter.notifyItemChanged(checkAllRow);
                checkableSessionCell.setChecked(isChecked);
            } else if (position == modeSectionRow) {
                AlertsCreator.showCheckableSettingModesAlert(this, getParentActivity(), getTitle(), this, null);
            } else if (position == checkAllRow) {
                AlertsCreator.showConfirmationDialog(this, context, selectedPasscodes.size() > 0
                                ? LocaleController.getString(R.string.Clear)
                                : LocaleController.getString(R.string.CheckAll),
                        () -> {
                            if (selectedPasscodes.size() > 0) {
                                selectedPasscodes = new ArrayList<>();
                            } else {
                                selectedPasscodes = new ArrayList<>(fakePasscodes);
                            }
                            listAdapter.notifyDataSetChanged();
                            saveCheckedPasscodes(selectedPasscodes);
                        });
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
        fakePasscodesSectionRow = -1;
        fakePasscodesStartRow = -1;

        modeSectionRow = rowCount++;
        checkAllRow = rowCount++;
        if (!fakePasscodes.isEmpty()) {
            fakePasscodesSectionRow = rowCount++;
            fakePasscodesStartRow = rowCount;
            rowCount += fakePasscodes.size();
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
            return position >= fakePasscodesStartRow
                    || position == modeSectionRow || position == checkAllRow;
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
                    view = new TextSettingsCell(mContext);
                    break;
                case 1:
                    view = new TextCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    break;
                default:
                    view = new TextCheckCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    String value;
                    switch (getSelectedMode()) {
                        case 0:
                            value = LocaleController.getString(R.string.Selected);
                            break;
                        case 1:
                            value = LocaleController.getString(R.string.ExceptSelected);
                            break;
                        default:
                            value = "";
                            break;
                    }
                    textSettingsCell.setTextAndValue(getTitle(), value, true);
                    break;
                case 1:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == checkAllRow) {
                        if (selectedPasscodes.size() > 0) {
                            textCell.setText(LocaleController.getString(R.string.Clear), true);
                        } else {
                            textCell.setText(LocaleController.getString(R.string.CheckAll), true);
                        }
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == fakePasscodesSectionRow) {
                        headerCell.setText(LocaleController.getString(R.string.FakePasscodes));
                    }
                    break;
                default:
                    TextCheckCell fakePasscodeCell = (TextCheckCell) holder.itemView;
                    if (position >= fakePasscodesStartRow) {
                        FakePasscode passcode = fakePasscodes.get(position - fakePasscodesStartRow);
                        boolean isChecked = selectedPasscodes.contains(passcode);
                        fakePasscodeCell.setTextAndCheck(passcode.name, isChecked, false);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == modeSectionRow) {
                return 0;
            } else if (position == checkAllRow) {
                return 1;
            } else if (position == fakePasscodesSectionRow) {
                return 2;
            } else if (position > fakePasscodesSectionRow) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_color_red));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }

    @Override
    public int getSelectedMode() {
        return currentPasscode.deletePasscodesAfterActivation.getMode();
    }

    @Override
    public void didSelectedMode(int mode) {
        currentPasscode.deletePasscodesAfterActivation.setMode(mode);
        SharedConfig.saveConfig();
        listAdapter.notifyDataSetChanged();
    }

    private String getTitle() {
        return LocaleController.getString(R.string.DeleteOtherPasscodesAfterActivation);
    }

    private void saveCheckedPasscodes(List<FakePasscode> passcodes) {
        currentPasscode.deletePasscodesAfterActivation.setSelected(passcodes.stream().map(passcode -> passcode.uuid).collect(Collectors.toList()));
        SharedConfig.saveConfig();
    }
}
