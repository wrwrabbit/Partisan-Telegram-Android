package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.partisan.settings.PartisanTelegramSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class PartisanTelegramSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    TextCheckCell frontPhotoTextCell;
    TextCheckCell backPhotoTextCell;

    private int rowCount;

    private int fakePasscodesHeaderRow;
    private int firstFakePasscodeRow;
    private int lastFakePasscodeRow;
    private int addFakePasscodeRow;
    private int restoreFakePasscodeDelimiterRow;
    private int restoreFakePasscodeRow;
    private int fakePasscodeDetailRow;

    private int bruteForceProtectionRow;
    private int bruteForceProtectionDetailRow;
    private int clearCacheOnLockRow;
    private int clearCacheOnLockDetailRow;
    private int badPasscodeAttemptsRow;
    private int badPasscodePhotoFrontRow;
    private int badPasscodePhotoBackRow;
    private int badPasscodeMuteAudioRow;
    private int badPasscodeDelimiterRow;
    private int securityIssuesRow;
    private int badPasscodeAttemptsDetailRow;

    private int protectPartisanSettingsRow;
    private int protectPartisanSettingsDetailRow;
    private int partisanTelegramSettingsPositionRow;
    private int partisanTelegramSettingsPositionDetailRow;
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
            if (position == bruteForceProtectionRow) {
                SharedConfig.bruteForceProtectionEnabled = !SharedConfig.bruteForceProtectionEnabled;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.bruteForceProtectionEnabled);
            } else if (position == clearCacheOnLockRow) {
                SharedConfig.clearCacheOnLock = !SharedConfig.clearCacheOnLock;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.clearCacheOnLock);
            } else if (position == badPasscodeAttemptsRow) {
                presentFragment(new BadPasscodeAttemptsActivity());
            } else if (position == badPasscodePhotoFrontRow) {
                showPhotoWarning(() -> {
                    Activity parentActivity = getParentActivity();
                    if (SharedConfig.takePhotoWithBadPasscodeFront
                            || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA)
                               == PackageManager.PERMISSION_GRANTED) {
                        SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                    } else {
                        ActivityCompat.requestPermissions(parentActivity,
                                new String[]{Manifest.permission.CAMERA}, 2000);
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                });
            } else if (position == badPasscodePhotoBackRow) {
                showPhotoWarning(() -> {
                    Activity parentActivity = getParentActivity();
                    if (SharedConfig.takePhotoWithBadPasscodeBack
                            || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA)
                               == PackageManager.PERMISSION_GRANTED) {
                        SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                    } else {
                        ActivityCompat.requestPermissions(parentActivity,
                                new String[]{Manifest.permission.CAMERA}, 2001);
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                });
            } else if (position == badPasscodeMuteAudioRow) {
                SharedConfig.takePhotoMuteAudio = !SharedConfig.takePhotoMuteAudio;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.takePhotoMuteAudio);
            } else if (position == securityIssuesRow) {
                presentFragment(new SecurityIssuesActivity());
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
            } else if (position == partisanTelegramSettingsPositionRow) {
                presentFragment(new org.telegram.messenger.partisan.ui.PartisanTelegramSettingsLocationFragment());
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

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if ((requestCode == 2000 || requestCode == 2001) && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (requestCode == 2000) {
                        SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                        SharedConfig.saveConfig();
                        if (frontPhotoTextCell != null) {
                            frontPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                        }
                    } else {
                        SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                        SharedConfig.saveConfig();
                        if (backPhotoTextCell != null) {
                            backPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                        }
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                });
            } else {
                new AlertDialog.Builder(getParentActivity())
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
        }
    }

    private void showPhotoWarning(Runnable callback) {
        if (SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack) {
            callback.run();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString(R.string.TakePhotoWarning));
            builder.setTitle(LocaleController.getString(R.string.Warning));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, v) -> callback.run());
            showDialog(builder.create());
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
        addFakePasscodeRow = -1;
        restoreFakePasscodeDelimiterRow = -1;
        restoreFakePasscodeRow = -1;
        fakePasscodeDetailRow = -1;
        bruteForceProtectionRow = -1;
        bruteForceProtectionDetailRow = -1;
        clearCacheOnLockRow = -1;
        clearCacheOnLockDetailRow = -1;
        badPasscodeAttemptsRow = -1;
        badPasscodePhotoFrontRow = -1;
        badPasscodePhotoBackRow = -1;
        badPasscodeMuteAudioRow = -1;
        badPasscodeDelimiterRow = -1;
        securityIssuesRow = -1;
        badPasscodeAttemptsDetailRow = -1;
        protectPartisanSettingsRow = -1;
        protectPartisanSettingsDetailRow = -1;
        partisanTelegramSettingsPositionRow = -1;
        partisanTelegramSettingsPositionDetailRow = -1;
        partisanSettingsRow = -1;
        partisanSettingsDetailRow = -1;

        fakePasscodesHeaderRow = rowCount++;
        if (!SharedConfig.fakePasscodes.isEmpty()) {
            firstFakePasscodeRow = rowCount;
            lastFakePasscodeRow = firstFakePasscodeRow + SharedConfig.fakePasscodes.size() - 1;
            rowCount = lastFakePasscodeRow + 1;
        }
        addFakePasscodeRow = rowCount++;
        restoreFakePasscodeDelimiterRow = rowCount++;
        restoreFakePasscodeRow = rowCount++;
        fakePasscodeDetailRow = rowCount++;

        bruteForceProtectionRow = rowCount++;
        bruteForceProtectionDetailRow = rowCount++;

        clearCacheOnLockRow = rowCount++;
        clearCacheOnLockDetailRow = rowCount++;

        badPasscodeAttemptsRow = rowCount++;
        badPasscodePhotoFrontRow = rowCount++;
        badPasscodePhotoBackRow = rowCount++;
        if (SharedConfig.takePhotoWithBadPasscodeBack || SharedConfig.takePhotoWithBadPasscodeFront) {
            badPasscodeMuteAudioRow = rowCount++;
        }
        badPasscodeDelimiterRow = rowCount++;
        securityIssuesRow = rowCount++;
        badPasscodeAttemptsDetailRow = rowCount++;

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

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == bruteForceProtectionRow
                    || position == clearCacheOnLockRow
                    || position == badPasscodeAttemptsRow
                    || position == badPasscodePhotoFrontRow
                    || position == badPasscodePhotoBackRow
                    || position == badPasscodeMuteAudioRow
                    || position == securityIssuesRow
                    || position == addFakePasscodeRow
                    || position == restoreFakePasscodeRow
                    || position == protectPartisanSettingsRow
                    || position == partisanTelegramSettingsPositionRow
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
                    } else if (position == bruteForceProtectionRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.BruteForceProtection), SharedConfig.bruteForceProtectionEnabled, false);
                    } else if (position == clearCacheOnLockRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ClearCacheOnLock), SharedConfig.clearCacheOnLock, false);
                    } else if (position == badPasscodePhotoFrontRow) {
                        frontPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString(R.string.TakePhotoWithFrontCamera), SharedConfig.takePhotoWithBadPasscodeFront, true);
                    } else if (position == badPasscodePhotoBackRow) {
                        backPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString(R.string.TakePhotoWithBackCamera), SharedConfig.takePhotoWithBadPasscodeBack, SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack);
                    } else if (position == badPasscodeMuteAudioRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.MuteAudioWhenTakingPhoto), SharedConfig.takePhotoMuteAudio, false);
                    }
                    break;
                }
                case VIEW_TYPE_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
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
                    } else if (position == badPasscodeAttemptsRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.BadPasscodeAttempts), String.valueOf(SharedConfig.getBadPasscodeAttemptList().size()), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == securityIssuesRow) {
                        textCell.setTextAndValue(LocaleController.getString(R.string.SecurityIssuesTitle), String.valueOf(getUserConfig().getActiveSecurityIssues().size()), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == partisanTelegramSettingsPositionRow) {
                        String[] positionOptions = {
                            LocaleController.getString(R.string.Settings),
                            LocaleController.getString(R.string.PrivacySettings),
                            LocaleController.getString(R.string.PartisanTelegramSettingsPositionDisabled)
                        };
                        int pos = PartisanTelegramSettings.partisanTelegramSettingsLocation.getOrDefault().ordinal();
                        textCell.setTextAndValue(LocaleController.getString(R.string.PartisanTelegramSettingsPosition), positionOptions[pos], false);
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
                    } else if (position == bruteForceProtectionDetailRow) {
                        cell.setText(LocaleController.getString(R.string.BruteForceProtectionInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == clearCacheOnLockDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ClearCacheOnLockInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    } else if (position == badPasscodeAttemptsDetailRow) {
                        cell.setText(LocaleController.getString(R.string.BadPasscodeAttemptsInfo));
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
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == bruteForceProtectionRow || position == clearCacheOnLockRow
                    || position == badPasscodePhotoFrontRow || position == badPasscodePhotoBackRow
                    || position == badPasscodeMuteAudioRow || position == protectPartisanSettingsRow) {
                return VIEW_TYPE_CHECK;
            } else if ((firstFakePasscodeRow != -1 && firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == addFakePasscodeRow || position == restoreFakePasscodeRow
                    || position == badPasscodeAttemptsRow || position == securityIssuesRow
                    || position == partisanTelegramSettingsPositionRow || position == partisanSettingsRow) {
                return VIEW_TYPE_SETTING;
            } else if (position == fakePasscodeDetailRow || position == bruteForceProtectionDetailRow
                    || position == clearCacheOnLockDetailRow || position == badPasscodeAttemptsDetailRow
                    || position == protectPartisanSettingsDetailRow || position == partisanTelegramSettingsPositionDetailRow
                    || position == partisanSettingsDetailRow) {
                return VIEW_TYPE_INFO;
            } else if (position == fakePasscodesHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == restoreFakePasscodeDelimiterRow || position == badPasscodeDelimiterRow) {
                return VIEW_TYPE_SHADOW;
            }
            return VIEW_TYPE_CHECK;
        }
    }
}
