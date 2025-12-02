package org.telegram.ui;

import android.util.Pair;
import android.widget.Toast;

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
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.settings.TesterSettings;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.SecurityChecker;
import org.telegram.messenger.partisan.SecurityIssue;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupInnerChatStarter;
import org.telegram.messenger.partisan.ui.AbstractItem;
import org.telegram.messenger.partisan.ui.ButtonItem;
import org.telegram.messenger.partisan.ui.DelimiterItem;
import org.telegram.messenger.partisan.ui.HeaderItem;
import org.telegram.messenger.partisan.ui.PartisanBaseFragment;
import org.telegram.messenger.partisan.ui.ReadOnlyDataItem;
import org.telegram.messenger.partisan.ui.SeekBarItem;
import org.telegram.messenger.partisan.ui.SimpleEditableDataItem;
import org.telegram.messenger.partisan.ui.ToggleItem;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.messenger.partisan.verification.VerificationStorage;
import org.telegram.messenger.partisan.verification.VerificationUpdatesChecker;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettings;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TesterSettingsFragment extends PartisanBaseFragment {
    @Override
    protected String getTitle() {
        return "Tester settings";
    }

    @Override
    protected AbstractItem[] createItems() {
        return new AbstractItem[] {
                new HeaderItem(this, "Update"),
                new SimpleEditableDataItem(this, "Update Channel Id",
                        () -> TesterSettings.updateChannelIdOverride.get().get() != 0 ? Long.toString(TesterSettings.updateChannelIdOverride.get().get()) : "",
                        value -> TesterSettings.updateChannelIdOverride.set(Utilities.parseLong(value))
                ),
                new SimpleEditableDataItem(this, "Update Channel Username", TesterSettings.updateChannelUsernameOverride),
                new ButtonItem(this, "Reset Update", view -> resetUpdate()),
                new DelimiterItem(this),


                new HeaderItem(this, "Verification"),
                new ButtonItem(this, "Check Verification Updates", view -> checkVerificationUpdates()),
                new ButtonItem(this, "Reset Verification Last Check Time", view -> resetVerificationLastCheckTime()),
                new DelimiterItem(this),


                new HeaderItem(this, "Secret Groups"),
                new ToggleItem(this, "Show Sec. Chats From Groups", TesterSettings.showEncryptedChatsFromEncryptedGroups),
                new ToggleItem(this, "Detailed Secret Group Member Status", TesterSettings.detailedEncryptedGroupMemberStatus),
                new ReadOnlyDataItem(this, "Flood Wait",
                        () -> "" + EncryptedGroupInnerChatStarter.getInstance(currentAccount).getFloodWaitRemaining()
                ),
                new DelimiterItem(this),


                new HeaderItem(this, "Voice Changing").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SimpleEditableDataItem(this, "Spectrum Distortion Params", VoiceChangeSettings.spectrumDistortionParams).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "World F0 Shift").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.f0Shift, 0.2, 2.01, 0.025).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "World Formant Low Ratio").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.lowRatio, 0.2, 2.01, 0.025).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "World Formant Mid Ratio").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.midRatio, 0.2, 2.01, 0.025).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "World Formant High Ratio").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.highRatio, 0.2, 2.01, 0.025).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Bad S Threshold").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.badSThreshold, 0, 15000, 250).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Bad S Cutoff").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.badSCutoff, 0, 15000, 250).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Bad Sh Min Threshold").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.badShMinThreshold, 0, 15000, 250).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Bad Sh Max Threshold").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.badShMaxThreshold, 0, 15000, 250).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Bad Sh Cutoff").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.badShCutoff, 0, 15000, 250).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new ToggleItem(this, "Harvest", VoiceChangeSettings.formantShiftingHarvest).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new HeaderItem(this, "Max Formant Spread").addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new SeekBarItem(this, VoiceChangeSettings.maxFormantSpread, 0.0, 1.0, 0.01).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new ToggleItem(this, "Use Old Window Restore", VoiceChangeSettings.useOldWindowRestore).addCondition(TesterSettings::areVoiceChangingSettingsVisible),
                new DelimiterItem(this).addCondition(TesterSettings::areVoiceChangingSettingsVisible),


                new HeaderItem(this, "Other Settings"),
                new ToggleItem(this, "Show Terminate Sessions Warning",
                        () -> SharedConfig.showSessionsTerminateActionWarning,
                        value -> SharedConfig.showSessionsTerminateActionWarning = value
                ),
                new ToggleItem(this, "Show Plain Backup", TesterSettings.showPlainBackup),
                new ToggleItem(this, "Disable Premium", TesterSettings.premiumDisabled),
                new ToggleItem(this, "Show Hide Dialog Is Not Safe Warning",
                        () -> SharedConfig.showHideDialogIsNotSafeWarning,
                        value -> SharedConfig.showHideDialogIsNotSafeWarning = value
                ),
                new SimpleEditableDataItem(this, "Phone Override", TesterSettings.phoneOverride)
                        .addCondition(() -> SharedConfig.activatedTesterSettingType == 2),
                new ButtonItem(this, "Reset Security Issues", view -> {
                    setSecurityIssues(new HashSet<>());
                    SecurityChecker.checkSecurityIssuesAndSave(getParentActivity(), getCurrentAccount(), true);
                    Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
                }),
                new ButtonItem(this, "Activate All Security Issues", view -> {
                    setSecurityIssues(new HashSet<>(Arrays.asList(SecurityIssue.values())));
                    Toast.makeText(getParentActivity(), "Activated", Toast.LENGTH_SHORT).show();
                }),
                new SimpleEditableDataItem(this, "Saved Channels",
                        this::getSavedChannelsValue,
                        this::setSavedChannels,
                        ()  -> Integer.toString(getUserConfig().savedChannels.size())
                ).setMultiline(),
                new ToggleItem(this, "Force Allow Screenshots", TesterSettings.forceAllowScreenshots)
                        .addCondition(() -> SharedConfig.activatedTesterSettingType == 2),
                new ToggleItem(this, "Save Logcat After Restart", TesterSettings.saveLogcatAfterRestart),
                new ToggleItem(this, "Clear Logs With Cache", TesterSettings.clearLogsWithCache),
                new ToggleItem(this, "Force Search During Deletion", TesterSettings.forceSearchDuringDeletion),
                new ToggleItem(this, "More Timer Values", TesterSettings.moreTimerValues),
                new DelimiterItem(this),


                new HeaderItem(this, "Stats"),
                new ReadOnlyDataItem(this, "Dialogs Count (all type)",
                        createDialogsCountFormatter(did -> true)
                ),
                new ReadOnlyDataItem(this, "Channel Count",
                        createDialogsCountFormatter(did -> ChatObject.isChannelAndNotMegaGroup(-did, currentAccount))
                ),
                new ReadOnlyDataItem(this, "Chat (Groups) Count",
                        createDialogsCountFormatter(did -> did < 0 && !ChatObject.isChannelAndNotMegaGroup(-did, currentAccount))
                ),
                new ReadOnlyDataItem(this, "User Chat Count",
                        createDialogsCountFormatter(did -> did > 0)
                ),
                new ReadOnlyDataItem(this, "Memory DB size",
                        () -> getMemoryDbSize() != null ? AndroidUtilities.formatFileSize(getMemoryDbSize()) : "error")
                        .setOnClickListener(this::showMemoryDialog)
                        .addCondition(() -> getMessagesStorage().fileProtectionEnabled()),
                new ReadOnlyDataItem(this, "Account num", () -> Integer.toString(currentAccount)),
                new DelimiterItem(this),
        };
    }

    private Supplier<String> createDialogsCountFormatter(Predicate<Long> condition) {
        return () -> {
            long count = getAllDialogs().stream().filter(d -> condition.test(d.id)).count();
            if (isDialogEndReached()) {
                return Long.toString(count);
            } else {
                return count + "(not all)";
            }
        };
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

    private String getSavedChannelsValue() {
        return getUserConfig().savedChannels.stream().reduce("", (acc, name) -> {
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
    }

    private void setSavedChannels(String text) {
        getUserConfig().pinnedSavedChannels = new ArrayList<>();
        getUserConfig().savedChannels = new HashSet<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String name = line.replace("*", "");
            if (line.startsWith("*")) {
                getUserConfig().pinnedSavedChannels.add(name);
            }
            getUserConfig().savedChannels.add(name);
        }
        getUserConfig().saveConfig(false);
    }

    private void resetUpdate() {
        PartisanLog.d("pendingPtgAppUpdate: reset 4");
        SharedConfig.pendingPtgAppUpdate = null;
        SharedConfig.saveConfig();
        Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    private void checkVerificationUpdates() {
        VerificationUpdatesChecker.checkUpdate(currentAccount, true);
        Toast.makeText(getParentActivity(), "Check started", Toast.LENGTH_SHORT).show();
    }

    private void resetVerificationLastCheckTime() {
        for (VerificationStorage storage : VerificationRepository.getInstance().getStorages()) {
            VerificationRepository.getInstance().saveNextCheckTime(storage.chatId, 0);
        }
        Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
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

    private void showMemoryDialog() {
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

    private List<TLRPC.Dialog> getAllDialogs() {
        return Utils.getAllDialogs(currentAccount);
    }
}

