package org.telegram.ui;

import android.content.Context;
import android.os.SystemClock;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteDatabaseWrapper;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.SecurityChecker;
import org.telegram.messenger.partisan.SecurityIssue;
import org.telegram.messenger.partisan.appmigration.MaskedMigratorHelper;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupInnerChatStarter;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.messenger.partisan.verification.VerificationStorage;
import org.telegram.messenger.partisan.verification.VerificationUpdatesChecker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class TesterSettingsActivity extends BaseFragment {

    private static class SimpleData {
        public String name;
        public Supplier<String> getValue;

        public SimpleData(String name, Supplier<String> getValue) {
            this.name = name;
            this.getValue = getValue;
        }
    }

    SimpleData[] simpleDataArray = {
            new SimpleData("Dialogs Count (all type)", () ->
                    getAllDialogs().size() + (!isDialogEndReached() ? " (not all)" : "")),
            new SimpleData("Channel Count", () ->
                    getAllDialogs().stream().filter(d -> ChatObject.isChannelAndNotMegaGroup(-d.id, currentAccount)).count()
                            + (!isDialogEndReached() ? " (not all)" : "")),
            new SimpleData("Chat (Groups) Count", () ->
                    getAllDialogs().stream().filter(d -> d.id < 0 && !ChatObject.isChannelAndNotMegaGroup(-d.id, currentAccount)).count()
                            + (!isDialogEndReached() ? " (not all)" : "")),
            new SimpleData("User Chat Count", () ->
                    getAllDialogs().stream().filter(d -> d.id > 0).count()
                            + (!isDialogEndReached() ? " (not all)" : "")),
            new SimpleData("Sec Group Flood Wait", () -> "" + EncryptedGroupInnerChatStarter.getInstance(currentAccount).getFloodWaitRemaining()),
    };

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int rowCount;

    private int sessionTerminateActionWarningRow;
    private int updateChannelIdRow;
    private int updateChannelUsernameRow;
    private int showPlainBackupRow;
    private int disablePremiumRow;
    private int simpleDataStartRow;
    private int simpleDataEndRow;
    private int hideDialogIsNotSafeWarningRow;
    private int phoneOverrideRow;
    private int resetSecurityIssuesRow;
    private int activateAllSecurityIssuesRow;
    private int editSavedChannelsRow;
    private int resetUpdateRow;
    private int checkVerificationUpdatesRow;
    private int resetVerificationLastCheckTimeRow;
    private int resetMaskedUpdateTagRow;
    private int showPermissionDisabledDialogRow;
    private int forceAllowScreenshotsRow;
    private int saveLogcatAfterRestartRow;
    private int showEncryptedChatsFromEncryptedGroupsRow;
    private int detailedEncryptedGroupMemberStatusRow;
    private int dbSizeRow;
    private int accountNumRow;
    private int clearLogsWithCacheRow;

    public static boolean showPlainBackup;

    public TesterSettingsActivity() {
        super();
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

        actionBar.setTitle("Tester settings");
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
            if (position == sessionTerminateActionWarningRow) {
                SharedConfig.showSessionsTerminateActionWarning = !SharedConfig.showSessionsTerminateActionWarning;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showSessionsTerminateActionWarning);
            } else if (position == updateChannelIdRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                String title = "Update Channel Id";
                template.title = title;
                long id = SharedConfig.updateChannelIdOverride;
                template.addNumberEditTemplate(id != 0 ? Long.toString(id) : "", "Channel Id", true);
                template.positiveListener = views -> {
                    long newId = Long.parseLong(((EditTextCaption)views.get(0)).getText().toString());
                    SharedConfig.updateChannelIdOverride = newId;
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, newId != 0 ? Long.toString(newId) : "", true);
                };
                template.negativeListener = (dlg, whichButton) -> {
                    SharedConfig.updateChannelIdOverride = 0;
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, "", true);
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == updateChannelUsernameRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                String title = "Update Channel Username";
                template.title = title;
                String value = SharedConfig.updateChannelUsernameOverride;
                template.addEditTemplate(value, "Channel Username", true);
                template.positiveListener = views -> {
                    String username = ((EditTextCaption)views.get(0)).getText().toString();
                    username = Utils.removeUsernamePrefixed(username);
                    SharedConfig.updateChannelUsernameOverride = username;
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, username, false);
                };
                template.negativeListener = (dlg, whichButton) -> {
                    SharedConfig.updateChannelUsernameOverride = "";
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, "", false);
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == showPlainBackupRow) {
                showPlainBackup = !showPlainBackup;
                ((TextCheckCell) view).setChecked(showPlainBackup);
            } else if (position == disablePremiumRow) {
                SharedConfig.premiumDisabled = !SharedConfig.premiumDisabled;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.premiumDisabled);
            } else if (position == hideDialogIsNotSafeWarningRow) {
                SharedConfig.showHideDialogIsNotSafeWarning = !SharedConfig.showHideDialogIsNotSafeWarning;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showHideDialogIsNotSafeWarning);
            } else if (position == phoneOverrideRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                String title = "Phone Override";
                template.title = title;
                String value = SharedConfig.phoneOverride;
                template.addEditTemplate(value, "Phone Override", true);
                template.positiveListener = views -> {
                    String phoneOverride = ((EditTextCaption)views.get(0)).getText().toString();
                    SharedConfig.phoneOverride = phoneOverride;
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, phoneOverride, true);
                };
                template.negativeListener = (dlg, whichButton) -> {
                    SharedConfig.phoneOverride = "";
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, "", true);
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == resetSecurityIssuesRow) {
                setSecurityIssues(new HashSet<>());
                SecurityChecker.checkSecurityIssuesAndSave(getParentActivity(), getCurrentAccount(), true);
                Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
            } else if (position == activateAllSecurityIssuesRow) {
                setSecurityIssues(new HashSet<>(Arrays.asList(SecurityIssue.values())));
                Toast.makeText(getParentActivity(), "Activated", Toast.LENGTH_SHORT).show();
            } else if (position == editSavedChannelsRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                String title = "Saved Channels";
                template.title = title;
                String value = getUserConfig().savedChannels.stream().reduce("", (acc, name) -> {
                    String result = acc;
                    if (!acc.isEmpty()) {
                        result += "\n";
                    }
                    if (getUserConfig().pinnedSavedChannels.contains(name)) {
                        result += "*";
                    }
                    result += name;
                    return result;
                });
                template.addEditTemplate(value, "Phone Override", false);
                template.positiveListener = views -> {
                    String text = ((EditTextCaption)views.get(0)).getText().toString();
                    getUserConfig().pinnedSavedChannels = new ArrayList<>();
                    getUserConfig().savedChannels = new HashSet<>();
                    for (String line : text.split("\n")) {
                        String name = line.replace("*", "");
                        if (line.startsWith("*")) {
                            getUserConfig().pinnedSavedChannels.add(name);
                        }
                        getUserConfig().savedChannels.add(name);
                    }
                    getUserConfig().saveConfig(false);
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, Integer.toString(getUserConfig().savedChannels.size()), true);
                };
                template.negativeListener = (dlg, whichButton) -> {
                    getUserConfig().pinnedSavedChannels = new ArrayList<>();
                    getUserConfig().savedChannels = new HashSet<>();
                    getUserConfig().saveConfig(false);
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(title, "0", true);
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == resetUpdateRow) {
                PartisanLog.d("pendingPtgAppUpdate: reset 4");
                SharedConfig.pendingPtgAppUpdate = null;
                SharedConfig.saveConfig();
                Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            } else if (position == checkVerificationUpdatesRow) {
                VerificationUpdatesChecker.checkUpdate(currentAccount, true);
                Toast.makeText(getParentActivity(), "Check started", Toast.LENGTH_SHORT).show();
            } else if (position == resetVerificationLastCheckTimeRow) {
                for (VerificationStorage storage : VerificationRepository.getInstance().getStorages()) {
                    VerificationRepository.getInstance().saveNextCheckTime(storage.chatId, 0);
                }
                Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
            } else if (position == resetMaskedUpdateTagRow) {
                SharedConfig.pendingPtgAppUpdate.botRequestTag = null;
                SharedConfig.saveConfig();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.maskedUpdateReceived);
                Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
            } else if (position == showPermissionDisabledDialogRow) {
                SharedConfig.showPermissionDisabledDialog = !SharedConfig.showPermissionDisabledDialog;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.showPermissionDisabledDialog);
            } else if (position == forceAllowScreenshotsRow) {
                SharedConfig.forceAllowScreenshots = !SharedConfig.forceAllowScreenshots;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.forceAllowScreenshots);
            } else if (position == saveLogcatAfterRestartRow) {
                SharedConfig.saveLogcatAfterRestart = !SharedConfig.saveLogcatAfterRestart;
                SharedConfig.saveConfig();
                ((TextCheckCell) view).setChecked(SharedConfig.saveLogcatAfterRestart);
            } else if (position == showEncryptedChatsFromEncryptedGroupsRow) {
                SharedConfig.toggleShowEncryptedChatsFromEncryptedGroups();
                ((TextCheckCell) view).setChecked(SharedConfig.showEncryptedChatsFromEncryptedGroups);
            } else if (position == detailedEncryptedGroupMemberStatusRow) {
                SharedConfig.toggleDetailedEncryptedGroupMemberStatus();
                ((TextCheckCell) view).setChecked(SharedConfig.detailedEncryptedGroupMemberStatus);
            } else if (position == dbSizeRow) {
                List<Pair<String, Long>> tableSizes = getTableSizes();
                String message = tableSizes.stream()
                        .map(pair -> pair.first + " = " + AndroidUtilities.formatFileSize(pair.second) + "\n")
                        .reduce(String::concat)
                        .orElse("");

                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(message);
                builder.setTitle(LocaleController.getString(R.string.AppName));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
            } else if (position == clearLogsWithCacheRow) {
                SharedConfig.toggleClearLogsWithCache();
                ((TextCheckCell) view).setChecked(SharedConfig.clearLogsWithCache);
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

        phoneOverrideRow = -1;
        forceAllowScreenshotsRow = -1;
        dbSizeRow = -1;

        sessionTerminateActionWarningRow = rowCount++;
        updateChannelIdRow = rowCount++;
        updateChannelUsernameRow = rowCount++;
        showPlainBackupRow = rowCount++;
        disablePremiumRow = rowCount++;
        simpleDataStartRow = rowCount;
        rowCount += simpleDataArray.length;
        simpleDataEndRow = rowCount;
        hideDialogIsNotSafeWarningRow = rowCount++;
        if (SharedConfig.activatedTesterSettingType >= 2) {
            phoneOverrideRow = rowCount++;
        }
        resetSecurityIssuesRow = rowCount++;
        activateAllSecurityIssuesRow = rowCount++;
        editSavedChannelsRow = rowCount++;
        resetUpdateRow = rowCount++;
        checkVerificationUpdatesRow = rowCount++;
        resetVerificationLastCheckTimeRow = rowCount++;
        if (SharedConfig.pendingPtgAppUpdate != null && SharedConfig.pendingPtgAppUpdate.botRequestTag != null) {
            resetMaskedUpdateTagRow = rowCount++;
        } else {
            resetMaskedUpdateTagRow = -1;
        }
        showPermissionDisabledDialogRow = rowCount++;
        if (SharedConfig.activatedTesterSettingType >= 2) {
            forceAllowScreenshotsRow = rowCount++;
        }
        saveLogcatAfterRestartRow = rowCount++;
        showEncryptedChatsFromEncryptedGroupsRow = rowCount++;
        detailedEncryptedGroupMemberStatusRow = rowCount++;
        if (getMessagesStorage().fileProtectionEnabled()) {
            dbSizeRow = rowCount++;
        }
        accountNumRow = rowCount++;
        clearLogsWithCacheRow = rowCount++;
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

    private void makeAndSendZip() {
        AlertDialog[] progressDialog = new AlertDialog[1];
        AndroidUtilities.runOnUIThread(() -> {
            progressDialog[0] = new AlertDialog(getParentActivity(), 3);
            progressDialog[0].setCanCancel(false);
            progressDialog[0].showDelayed(300);
        });
        progressDialog[0].dismiss();
    }

    private boolean isDialogEndReached() {
        MessagesController controller = getMessagesController();
        return controller.isDialogsEndReached(0) && controller.isServerDialogsEndReached(0)
                && (!hasArchive() || controller.isDialogsEndReached(1) && controller.isServerDialogsEndReached(1));
    }

    private boolean hasArchive() {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (controller.dialogs_dict.get(DialogObject.makeFolderDialogId(1)) == null) {
            return false;
        }
        List<TLRPC.Dialog> dialogs = controller.getDialogs(1);
        return dialogs != null && !dialogs.isEmpty();
    }

    private List<TLRPC.Dialog> getAllDialogs() {
        return Utils.getAllDialogs(currentAccount);
    }

    private void setSecurityIssues(Set<SecurityIssue> issues) {
        SharedConfig.ignoredSecurityIssues = new HashSet<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            config.currentSecurityIssues = issues;
            config.ignoredSecurityIssues = new HashSet<>();
            config.lastSecuritySuggestionsShow = 0;
            config.showSecuritySuggestions = !issues.isEmpty();
            config.saveConfig(false);
        }
    }

    private Long getMemoryDbSize() {
        Long dbSize = null;
        SQLiteDatabase database = getMessagesStorage().getDatabase();
        if (database instanceof SQLiteDatabaseWrapper) {
            SQLiteDatabaseWrapper wrapper = (SQLiteDatabaseWrapper)database;
            SQLiteDatabase memoryDatabase = wrapper.getMemoryDatabase();
            try {
                SQLiteCursor cursor = memoryDatabase.queryFinalized("select page_count * page_size from pragma_page_count(), pragma_page_size()");
                if (cursor.next()) {
                    dbSize = cursor.longValue(0);
                }
                cursor.dispose();
            } catch (Exception ignore) {
            }
        }
        return dbSize;
    }

    private List<Pair<String, Long>> getTableSizes() {
        List<Pair<String, Long>> tableSizes = new ArrayList<>();
        SQLiteDatabase database = getMessagesStorage().getDatabase();
        if (database instanceof SQLiteDatabaseWrapper) {
            SQLiteDatabaseWrapper wrapper = (SQLiteDatabaseWrapper)database;
            SQLiteDatabase memoryDatabase = wrapper.getMemoryDatabase();
            try {
                SQLiteCursor cursor = memoryDatabase.queryFinalized("SELECT name, SUM(pgsize) size FROM \"dbstat\" GROUP BY name ORDER BY size DESC LIMIT 20");
                while (cursor.next()) {
                    String name = cursor.stringValue(0);
                    long size = cursor.longValue(1);
                    tableSizes.add(new Pair<>(name, size));
                }
                cursor.dispose();
            } catch (Exception e) {
                PartisanLog.e("Error", e);
            }
        }
        return tableSizes;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position >= simpleDataStartRow && position < simpleDataEndRow) {
                return false;
            }
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                default:
                    view = new TextSettingsCell(mContext);
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
                    if (position == sessionTerminateActionWarningRow) {
                        textCell.setTextAndCheck("Show terminate sessions warning",
                                SharedConfig.showSessionsTerminateActionWarning, true);
                    } else if (position == showPlainBackupRow) {
                        textCell.setTextAndCheck("Show plain backup", showPlainBackup, true);
                    } else if (position == disablePremiumRow) {
                        textCell.setTextAndCheck("Disable Premium", SharedConfig.premiumDisabled, true);
                    } else if (position == hideDialogIsNotSafeWarningRow) {
                        textCell.setTextAndCheck("Show hide dialog is not safe warning",
                                SharedConfig.showHideDialogIsNotSafeWarning, true);
                    } else if (position == forceAllowScreenshotsRow) {
                        textCell.setTextAndCheck("Force allow screenshots",
                                SharedConfig.forceAllowScreenshots, true);
                    } else if (position == saveLogcatAfterRestartRow) {
                        textCell.setTextAndCheck("Save logcat after restart",
                                SharedConfig.saveLogcatAfterRestart, true);
                    } else if (position == showPermissionDisabledDialogRow) {
                        textCell.setTextAndCheck("Show permission disabled dialog",
                                SharedConfig.showPermissionDisabledDialog, true);
                    } else if (position == showEncryptedChatsFromEncryptedGroupsRow) {
                        textCell.setTextAndCheck("Show sec. chats from sec. groups",
                                SharedConfig.showEncryptedChatsFromEncryptedGroups, true);
                    } else if (position == detailedEncryptedGroupMemberStatusRow) {
                        textCell.setTextAndCheck("Detailed Secret Group Member Status",
                                SharedConfig.detailedEncryptedGroupMemberStatus, true);
                    } else if (position == clearLogsWithCacheRow) {
                        textCell.setTextAndCheck("Clear logs with cache",
                                SharedConfig.clearLogsWithCache, true);
                    }
                    break;
                } case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == updateChannelIdRow) {
                        long id = SharedConfig.updateChannelIdOverride;
                        textCell.setTextAndValue("Update Channel Id", id != 0 ? Long.toString(id) : "", true);
                    } else if (position == updateChannelUsernameRow) {
                        textCell.setTextAndValue("Update Channel Username", SharedConfig.updateChannelUsernameOverride, true);
                    } else if (simpleDataStartRow <= position && position < simpleDataEndRow) {
                        SimpleData simpleData = simpleDataArray[position - simpleDataStartRow];
                        textCell.setTextAndValue(simpleData.name, simpleData.getValue.get(), true);
                    } else if (position == phoneOverrideRow) {
                        textCell.setTextAndValue("Phone Override", SharedConfig.phoneOverride, true);
                    } else if (position == resetSecurityIssuesRow) {
                        textCell.setText("Reset Security Issues", true);
                    } else if (position == activateAllSecurityIssuesRow) {
                        textCell.setText("Activate All Security Issues", true);
                    } else if (position == editSavedChannelsRow) {
                        textCell.setTextAndValue("Saved Channels", Integer.toString(getUserConfig().savedChannels.size()), true);
                    } else if (position == resetUpdateRow) {
                        textCell.setText("Reset Update", true);
                    } else if (position == checkVerificationUpdatesRow) {
                        textCell.setText("Check Verification Updates", true);
                    } else if (position == resetVerificationLastCheckTimeRow) {
                        textCell.setText("Reset Verification Last Check Time", true);
                    } else if (position == resetMaskedUpdateTagRow) {
                        textCell.setText("Reset Masked Update Tag", true);
                    } else if (position == dbSizeRow) {
                        Long databaseSize = getMemoryDbSize();
                        textCell.setTextAndValue("Memory DB size", databaseSize != null ? AndroidUtilities.formatFileSize(databaseSize) : "error", true);
                    } else if (position == accountNumRow) {
                        textCell.setTextAndValue("Account num", Integer.toString(currentAccount), true);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == sessionTerminateActionWarningRow || position == showPlainBackupRow
                    || position == disablePremiumRow || position == hideDialogIsNotSafeWarningRow || position == showPermissionDisabledDialogRow
                    || position == forceAllowScreenshotsRow || position == saveLogcatAfterRestartRow
                    || position == showEncryptedChatsFromEncryptedGroupsRow || position == detailedEncryptedGroupMemberStatusRow
                    || position == clearLogsWithCacheRow) {
                return 0;
            } else if (position == updateChannelIdRow || position == updateChannelUsernameRow
                    || (simpleDataStartRow <= position && position < simpleDataEndRow)
                    || position == phoneOverrideRow || position == resetSecurityIssuesRow
                    || position == activateAllSecurityIssuesRow || position == editSavedChannelsRow
                    || position == resetUpdateRow || position == checkVerificationUpdatesRow
                    || position == resetVerificationLastCheckTimeRow || position == resetMaskedUpdateTagRow
                    || position == dbSizeRow
                    || position == accountNumRow) {
                return 1;
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

