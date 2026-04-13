package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.messenger.partisan.ui.InterfaceTweaksFragment;
import org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLocationFragment;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CollapseTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class PartisanTelegramSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int rowCount;

    private static final int MAX_VISIBLE_FAKE_PASSCODES = 3;
    private static final int MIN_FOLD_FAKE_PASSCODES = 5;
    private boolean showAllFakePasscodes = false;

    private int fakePasscodesHeaderRow;
    private int firstFakePasscodeRow;
    private int lastFakePasscodeRow;
    private int showMoreFakePasscodesRow;
    private int addFakePasscodeRow;
    private int restoreFakePasscodeDelimiterRow;
    private int restoreFakePasscodeRow;
    private int fakePasscodeDetailRow;

    private int clearCacheOnLockRow;
    private int clearCacheOnLockDetailRow;
    private int badPasscodeReactionRow;
    private int badPasscodeReactionDetailRow;
    private int securityIssuesRow;
    private int securityIssuesDelimiterRow;

    private int protectPartisanSettingsRow;
    private int protectPartisanSettingsDetailRow;
    private int partisanTelegramSettingsPositionRow;
    private int partisanTelegramSettingsPositionDetailRow;
    private int interfaceTweaksRow;
    private int interfaceTweaksDetailRow;
    private int partisanSettingsRow;
    private int partisanSettingsDetailRow;

    public PartisanTelegramSettingsActivity() {
        super();
    }

    public static BaseFragment checkLockAndCreateActivity() {
        if (SharedConfig.protectPartisanSettings && SharedConfig.passcodeEnabled()) {
            return new org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLockActivity();
        }
        return new PartisanTelegramSettingsActivity();
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

        actionBar.setTitle(LocaleController.getString(R.string.PartisanTelegramSettings));
        frameLayout.setTag(Theme.key_windowBackgroundGray);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
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
                return;
            }
            if (position == clearCacheOnLockRow) {
                SharedConfig.clearCacheOnLock = !SharedConfig.clearCacheOnLock;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.clearCacheOnLock);
            } else if (position == badPasscodeReactionRow) {
                presentFragment(new org.telegram.messenger.partisan.ui.BadPasscodeReactionFragment());
            } else if (position == securityIssuesRow) {
                presentFragment(new SecurityIssuesFragment());
            } else if (position == showMoreFakePasscodesRow) {
                showAllFakePasscodes = true;
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (firstFakePasscodeRow != -1
                    && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                presentFragment(new FakePasscodeActivity(
                        FakePasscodeActivity.TYPE_FAKE_PASSCODE_SETTINGS,
                        SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow), false));
            } else if (position == addFakePasscodeRow) {
                showNoMainPasscodeWarningIfNeeded(() -> {
                    FakePasscode newFakePasscode = FakePasscode.create();
                    presentFragment(new FakePasscodeActivity(
                            FakePasscodeActivity.TYPE_SETUP_FAKE_PASSCODE, newFakePasscode, true));
                });
            } else if (position == restoreFakePasscodeRow) {
                showNoMainPasscodeWarningIfNeeded(() -> {
                    presentFragment(new FakePasscodeRestoreActivity());
                });
            } else if (position == protectPartisanSettingsRow) {
                SharedConfig.protectPartisanSettings = !SharedConfig.protectPartisanSettings;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.protectPartisanSettings);
            } else if (position == interfaceTweaksRow) {
                presentFragment(new InterfaceTweaksFragment());
            } else if (position == partisanTelegramSettingsPositionRow) {
                presentFragment(new PartisanTelegramSettingsLocationFragment());
            } else if (position == partisanSettingsRow) {
                presentFragment(new PartisanSettingsActivity());
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void showNoMainPasscodeWarningIfNeeded(Runnable action) {
        if (!SharedConfig.passcodeEnabled() && SharedConfig.showFakePasscodeNoMainPasscodeWarning) {
            showDialog(buildNoMainPasscodeWarning(action));
        } else {
            action.run();
        }
    }

    private AlertDialog buildNoMainPasscodeWarning(Runnable action) {
        CheckBox checkBox = new CheckBox(getParentActivity());
        checkBox.setText(LocaleController.getString(R.string.DoNotShowAgain));
        checkBox.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        layout.addView(checkBox);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.FakePasscodeNoMainPasscodeTitle));
        builder.setMessage(LocaleController.getString(R.string.FakePasscodeNoMainPasscodeMessage));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.CheckPhoneNumberYes), (dialog, which) -> {
            if (checkBox.isChecked()) {
                SharedConfig.showFakePasscodeNoMainPasscodeWarning = false;
                SharedConfig.saveConfig();
            }
            PasscodeActivity passcodeActivity = new PasscodeActivity(PasscodeActivity.TYPE_SETUP_CODE);
            passcodeActivity.returnToPreviousFragment = true;
            presentFragment(passcodeActivity);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.CheckPhoneNumberNo), (dialog, which) -> {
            if (checkBox.isChecked()) {
                SharedConfig.showFakePasscodeNoMainPasscodeWarning = false;
                SharedConfig.saveConfig();
            }
            action.run();
        });
        return builder.create();
    }

    private void updateRows() {
        rowCount = 0;

        fakePasscodesHeaderRow = -1;
        firstFakePasscodeRow = -1;
        lastFakePasscodeRow = -1;
        showMoreFakePasscodesRow = -1;
        addFakePasscodeRow = -1;
        restoreFakePasscodeDelimiterRow = -1;
        restoreFakePasscodeRow = -1;
        fakePasscodeDetailRow = -1;
        clearCacheOnLockRow = -1;
        clearCacheOnLockDetailRow = -1;
        badPasscodeReactionRow = -1;
        badPasscodeReactionDetailRow = -1;
        securityIssuesRow = -1;
        securityIssuesDelimiterRow = -1;
        protectPartisanSettingsRow = -1;
        protectPartisanSettingsDetailRow = -1;
        partisanTelegramSettingsPositionRow = -1;
        partisanTelegramSettingsPositionDetailRow = -1;
        interfaceTweaksRow = -1;
        interfaceTweaksDetailRow = -1;
        partisanSettingsRow = -1;
        partisanSettingsDetailRow = -1;

        fakePasscodesHeaderRow = rowCount++;
        boolean needFoldFakePasscodes = SharedConfig.fakePasscodes.size() >= MIN_FOLD_FAKE_PASSCODES;
        if (!SharedConfig.fakePasscodes.isEmpty()) {
            firstFakePasscodeRow = rowCount;
            int visibleCount = (!needFoldFakePasscodes || showAllFakePasscodes)
                    ? SharedConfig.fakePasscodes.size()
                    : MAX_VISIBLE_FAKE_PASSCODES;
            lastFakePasscodeRow = firstFakePasscodeRow + visibleCount - 1;
            rowCount = lastFakePasscodeRow + 1;
        }
        if (needFoldFakePasscodes && !showAllFakePasscodes) {
            showMoreFakePasscodesRow = rowCount++;
        }
        addFakePasscodeRow = rowCount++;
        restoreFakePasscodeDelimiterRow = rowCount++;
        restoreFakePasscodeRow = rowCount++;
        fakePasscodeDetailRow = rowCount++;

        clearCacheOnLockRow = rowCount++;
        clearCacheOnLockDetailRow = rowCount++;

        badPasscodeReactionRow = rowCount++;
        badPasscodeReactionDetailRow = rowCount++;
        securityIssuesRow = rowCount++;
        securityIssuesDelimiterRow = rowCount++;

        interfaceTweaksRow = rowCount++;
        interfaceTweaksDetailRow = rowCount++;
        protectPartisanSettingsRow = rowCount++;
        protectPartisanSettingsDetailRow = rowCount++;
        partisanTelegramSettingsPositionRow = rowCount++;
        partisanTelegramSettingsPositionDetailRow = rowCount++;
        partisanSettingsRow = rowCount++;
        partisanSettingsDetailRow = rowCount++;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private static final int VIEW_TYPE_CHECK   = 0;
        private static final int VIEW_TYPE_SETTING = 1;
        private static final int VIEW_TYPE_INFO    = 2;
        private static final int VIEW_TYPE_HEADER  = 3;
        private static final int VIEW_TYPE_SHADOW  = 4;
        private static final int VIEW_TYPE_MORE    = 5;

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == clearCacheOnLockRow
                    || position == badPasscodeReactionRow
                    || position == securityIssuesRow
                    || position == addFakePasscodeRow
                    || position == restoreFakePasscodeRow
                    || position == showMoreFakePasscodesRow
                    || position == protectPartisanSettingsRow
                    || position == partisanTelegramSettingsPositionRow
                    || position == interfaceTweaksRow
                    || position == partisanSettingsRow
                    || (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow);
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
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_MORE:
                    view = new CollapseTextCell(mContext, getResourceProvider());
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SHADOW:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CHECK: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == protectPartisanSettingsRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ProtectPartisanSettings), SharedConfig.protectPartisanSettings, false);
                    } else if (position == clearCacheOnLockRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearCacheOnLock), SharedConfig.clearCacheOnLock, false);
                    }
                    break;
                }
                case VIEW_TYPE_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.ellipsizeValueInsteadOfText = false;
                    if (firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                        textCell.setText(SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow).name, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == addFakePasscodeRow) {
                        textCell.setText(LocaleController.getString(R.string.AddFakePasscode), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == restoreFakePasscodeRow) {
                        textCell.setText(LocaleController.getString(R.string.FakePasscodeRestore), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == badPasscodeReactionRow) {
                        textCell.setText(LocaleController.getString(R.string.BadPasscodeReaction), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == securityIssuesRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.SecurityIssuesTitle), String.valueOf(getUserConfig().getActiveSecurityIssues().size()), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == partisanTelegramSettingsPositionRow) {
                        String[] positionOptions = {
                            LocaleController.getString(R.string.Settings),
                            LocaleController.getString(R.string.PrivacySettings),
                            LocaleController.getString(R.string.PartisanTelegramSettingsPositionDisabled)
                        };
                        int pos = PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault().ordinal();
                        textCell.ellipsizeValueInsteadOfText = true;
                        textCell.setTextAndValue(LocaleController.getString(R.string.PartisanTelegramSettingsPosition), positionOptions[pos], false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == interfaceTweaksRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.InterfaceTweaks), InterfaceTweaksFragment.getEnabledSummary(), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == partisanSettingsRow) {
                        textCell.setText(LocaleController.getString(R.string.OtherSettings), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == fakePasscodeDetailRow) {
                        cell.setText(LocaleController.getString(R.string.FakePasscodeActionsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == clearCacheOnLockDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ClearCacheOnLockInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == badPasscodeReactionDetailRow) {
                        cell.setText(LocaleController.getString(R.string.BadPasscodeReactionInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == protectPartisanSettingsDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ProtectPartisanSettingsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == partisanTelegramSettingsPositionDetailRow) {
                        cell.setText(LocaleController.getString(R.string.PartisanTelegramSettingsPositionInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == interfaceTweaksDetailRow) {
                        cell.setText(LocaleController.getString(R.string.InterfaceTweaksInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == partisanSettingsDetailRow) {
                        cell.setText(LocaleController.getString(R.string.PartisanSettingsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setHeight(46);
                    if (position == fakePasscodesHeaderRow) {
                        cell.setText(LocaleController.getString(R.string.FakePasscodes));
                    }
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    View sectionCell = holder.itemView;
                    sectionCell.setTag(position);
                    sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                }
                case VIEW_TYPE_MORE: {
                    CollapseTextCell collapseCell = (CollapseTextCell) holder.itemView;
                    collapseCell.set(LocaleController.getString(R.string.MoreAccounts), true);
                    collapseCell.setColor(Theme.key_windowBackgroundWhiteBlueText4);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == clearCacheOnLockRow || position == protectPartisanSettingsRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == showMoreFakePasscodesRow) {
                return VIEW_TYPE_MORE;
            } else if ((firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == addFakePasscodeRow || position == restoreFakePasscodeRow
                    || position == badPasscodeReactionRow || position == securityIssuesRow
                    || position == partisanTelegramSettingsPositionRow || position == interfaceTweaksRow
                    || position == partisanSettingsRow) {
                return VIEW_TYPE_SETTING;
            } else if (position == fakePasscodeDetailRow || position == clearCacheOnLockDetailRow
                    || position == badPasscodeReactionDetailRow
                    || position == protectPartisanSettingsDetailRow || position == partisanTelegramSettingsPositionDetailRow
                    || position == interfaceTweaksDetailRow || position == partisanSettingsDetailRow) {
                return VIEW_TYPE_INFO;
            } else if (position == fakePasscodesHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == restoreFakePasscodeDelimiterRow || position == securityIssuesDelimiterRow) {
                return VIEW_TYPE_SHADOW;
            }
            return VIEW_TYPE_CHECK;
        }
    }
}
