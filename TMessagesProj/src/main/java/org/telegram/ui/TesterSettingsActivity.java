package org.telegram.ui;

import android.content.Context;
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
import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.messenger.partisan.settings.FloatSetting;
import org.telegram.messenger.partisan.settings.StringSetting;
import org.telegram.messenger.partisan.settings.TesterSettings;
import org.telegram.messenger.partisan.Utils;
import org.telegram.messenger.partisan.SecurityChecker;
import org.telegram.messenger.partisan.SecurityIssue;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupInnerChatStarter;
import org.telegram.messenger.partisan.verification.VerificationRepository;
import org.telegram.messenger.partisan.verification.VerificationStorage;
import org.telegram.messenger.partisan.verification.VerificationUpdatesChecker;
import org.telegram.messenger.partisan.SeekBarCell;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TesterSettingsActivity extends BaseFragment {

    private enum ViewTypes {
        TOGGLE,
        BUTTON,
        HEADER,
        SEEK_BAR,
    };

    private static abstract class Item {
        private int position = -1;
        private final int viewType;
        protected final BaseFragment fragment;
        private Supplier<Boolean> condition = null;

        protected Item(BaseFragment fragment, int viewType) {
            this.fragment = fragment;
            this.viewType = viewType;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public boolean positionMatch(int targetPosition) {
            return position == targetPosition;
        }

        public int getViewType() {
            return viewType;
        }

        public Item addCondition(Supplier<Boolean> condition) {
            this.condition = condition;
            return this;
        }

        public boolean needAddRow() {
            if (condition != null) {
                return condition.get();
            }
            return true;
        }

        public abstract void onBindViewHolder(RecyclerView.ViewHolder holder, int position);
        public abstract void onClick(View view);
        public abstract boolean enabled();
    }

    private static class ToggleItem extends Item {
        private final String text;
        private final Supplier<Boolean> getValue;
        private final Consumer<Boolean> setValue;

        public ToggleItem(BaseFragment fragment, String text, BooleanSetting booleanSetting) {
            this(fragment, text, booleanSetting::get, booleanSetting::set);
        }

        public ToggleItem(BaseFragment fragment, String text, Supplier<Boolean> getValue, Consumer<Boolean> setValue) {
            super(fragment, ViewTypes.TOGGLE.ordinal());
            this.text = text;
            this.getValue = getValue;
            this.setValue = setValue;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((TextCheckCell) holder.itemView).setTextAndCheck(text, getValue.get(), true);
        }

        @Override
        public void onClick(View view) {
            setValue.accept(!getValue.get());
            ((TextCheckCell) view).setChecked(getValue.get());
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static class ButtonItem extends Item {
        private final String text;
        private final Runnable onClick;

        public ButtonItem(BaseFragment fragment, String text, Runnable onClick) {
            super(fragment, ViewTypes.BUTTON.ordinal());
            this.text = text;
            this.onClick = onClick;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((TextSettingsCell) holder.itemView).setText(text, true);
        }

        @Override
        public void onClick(View view) {
            onClick.run();
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static class DataItem extends Item {
        private final String text;
        private final Supplier<String> getValue;
        private final Runnable onClick;

        public DataItem(BaseFragment fragment, String text, Supplier<String> getValue) {
            this(fragment, text, getValue, null);
        }

        public DataItem(BaseFragment fragment, String text, Supplier<String> getValue, Runnable onClick) {
            super(fragment, ViewTypes.BUTTON.ordinal());
            this.text = text;
            this.getValue = getValue;
            this.onClick = onClick;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((TextSettingsCell) holder.itemView).setTextAndValue(text, getValue.get(), true);
        }

        @Override
        public void onClick(View view) {
            if (onClick != null) {
                onClick.run();
            }
        }

        @Override
        public boolean enabled() {
            return onClick != null;
        }
    }

    private static class EditableDataItem extends Item {
        private final String text;
        private final Supplier<String> getValue;
        private final Consumer<String> setValue;
        private final Supplier<String> getCellValue;
        private boolean multiline = false;

        public EditableDataItem(BaseFragment fragment, String text, StringSetting setting) {
            this(fragment, text, setting::get, setting::set);
        }

        public EditableDataItem(BaseFragment fragment, String text, Supplier<String> getValue, Consumer<String> setValue) {
            this(fragment, text, getValue, setValue, getValue);
        }

        public EditableDataItem(BaseFragment fragment, String text, Supplier<String> getValue, Consumer<String> setValue, Supplier<String> getCellValue) {
            super(fragment, ViewTypes.BUTTON.ordinal());
            this.text = text;
            this.getValue = getValue;
            this.setValue = setValue;
            this.getCellValue = getCellValue;
        }

        public EditableDataItem setMultiline() {
            this.multiline = true;
            return this;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((TextSettingsCell) holder.itemView).setTextAndValue(text, getCellValue.get(), true);
        }

        @Override
        public void onClick(View view) {
            DialogTemplate template = new DialogTemplate();
            template.type = DialogType.EDIT;
            template.title = text;
            String value = getValue.get();
            template.addEditTemplate(value, text, !multiline);
            TextSettingsCell cell = (TextSettingsCell) view;
            template.positiveListener = views -> {
                setValue.accept(((EditTextCaption)views.get(0)).getText().toString());
                cell.setTextAndValue(text, getCellValue.get(), true);
            };
            template.negativeListener = (dlg, whichButton) -> {
                setValue.accept("");
                cell.setTextAndValue(text, getCellValue.get(), true);
            };
            AlertDialog dialog = FakePasscodeDialogBuilder.build(fragment.getParentActivity(), template);
            fragment.showDialog(dialog);
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static class HeaderItem extends Item {
        private final String text;

        public HeaderItem(BaseFragment fragment, String text) {
            super(fragment, ViewTypes.HEADER.ordinal());
            this.text = text;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((HeaderCell) holder.itemView).setText(text);
        }

        @Override
        public void onClick(View view) {}

        @Override
        public boolean enabled() {
            return false;
        }
    }

    private static class SeekBarItem extends Item {
        private final Supplier<Float> getValue;
        private final Consumer<Float> setValue;

        public SeekBarItem(BaseFragment fragment, FloatSetting setting) {
            this(fragment, setting::get, setting::set);
        }

        public SeekBarItem(BaseFragment fragment, Supplier<Float> getValue, Consumer<Float> setValue) {
            super(fragment, ViewTypes.SEEK_BAR.ordinal());
            this.getValue = getValue;
            this.setValue = setValue;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            SeekBarCell seekBarCell = (SeekBarCell) holder.itemView;
            seekBarCell.setValues(generateValues().toArray(), getValue.get());
            seekBarCell.setDelegate(obj -> setValue.accept((float)obj));
            seekBarCell.invalidate();
        }

        private List<Object> generateValues() {
            List<Object> values = new ArrayList<>();
            for (float value = 0.2f; value <= 2.01f; value += 0.025f) {
                if (Math.abs(value - getValue.get()) < 0.01f) {
                    values.add(getValue.get());
                } else if (Math.abs(value - 1.0f) < 0.01f) {
                    values.add(1.0f);
                } else {
                    values.add(value);
                }
            }
            return values;
        }

        @Override
        public void onClick(View view) {}

        @Override
        public boolean enabled() {
            return true;
        }
    }



    Item[] items = {
            new ToggleItem(this, "Show terminate sessions warning",
                    () -> SharedConfig.showSessionsTerminateActionWarning,
                    value -> SharedConfig.showSessionsTerminateActionWarning = value
            ),
            new EditableDataItem(this, "Update Channel Id",
                    () -> TesterSettings.updateChannelIdOverride.get() != 0 ? Long.toString(TesterSettings.updateChannelIdOverride.get()) : "",
                    value -> TesterSettings.updateChannelIdOverride.set(Long.parseLong(value))
            ),
            new EditableDataItem(this, "Update Channel Username", TesterSettings.updateChannelUsernameOverride),
            new ToggleItem(this, "Show plain backup", TesterSettings.showPlainBackup),
            new ToggleItem(this, "Disable Premium", TesterSettings.premiumDisabled),
            new DataItem(this, "Dialogs Count (all type)",
                    createDialogsCountFormatter(did -> true)
            ),
            new DataItem(this, "Channel Count",
                    createDialogsCountFormatter(did -> ChatObject.isChannelAndNotMegaGroup(-did, currentAccount))
            ),
            new DataItem(this, "Chat (Groups) Count",
                    createDialogsCountFormatter(did -> did < 0 && !ChatObject.isChannelAndNotMegaGroup(-did, currentAccount))
            ),
            new DataItem(this, "User Chat Count",
                    createDialogsCountFormatter(did -> did > 0)
            ),
            new DataItem(this, "Sec Group Flood Wait",
                    () -> "" + EncryptedGroupInnerChatStarter.getInstance(currentAccount).getFloodWaitRemaining()
            ),
            new ToggleItem(this, "Show hide dialog is not safe warning",
                    () -> SharedConfig.showHideDialogIsNotSafeWarning,
                    value -> SharedConfig.showHideDialogIsNotSafeWarning = value
            ),
            new EditableDataItem(this, "Phone Override", TesterSettings.phoneOverride)
                    .addCondition(() -> SharedConfig.activatedTesterSettingType >= 2),
            new ButtonItem(this, "Reset Security Issues", () -> {
                setSecurityIssues(new HashSet<>());
                SecurityChecker.checkSecurityIssuesAndSave(getParentActivity(), getCurrentAccount(), true);
                Toast.makeText(getParentActivity(), "Reset", Toast.LENGTH_SHORT).show();
            }),
            new ButtonItem(this, "Activate All Security Issues", () -> {
                setSecurityIssues(new HashSet<>(Arrays.asList(SecurityIssue.values())));
                Toast.makeText(getParentActivity(), "Activated", Toast.LENGTH_SHORT).show();
            }),
            new EditableDataItem(this, "Saved Channels",
                    this::getSavedChannelsValue,
                    this::setSavedChannels,
                    ()  -> Integer.toString(getUserConfig().savedChannels.size())
            ).setMultiline(),
            new ButtonItem(this, "Reset Update", this::resetUpdate),
            new ButtonItem(this, "Check Verification Updates", this::checkVerificationUpdates),
            new ButtonItem(this, "Reset Verification Last Check Time", this::resetVerificationLastCheckTime),
            new ToggleItem(this, "Force allow screenshots", TesterSettings.forceAllowScreenshots)
                    .addCondition(() -> SharedConfig.activatedTesterSettingType >= 2),
            new ToggleItem(this, "Save logcat after restart", TesterSettings.saveLogcatAfterRestart),
            new ToggleItem(this, "Show sec. chats from sec. groups", TesterSettings.showEncryptedChatsFromEncryptedGroups),
            new ToggleItem(this, "Detailed Secret Group Member Status", TesterSettings.detailedEncryptedGroupMemberStatus),
            new DataItem(this, "Memory DB size",
                    () -> getMemoryDbSize() != null ? AndroidUtilities.formatFileSize(getMemoryDbSize()) : "error",
                    this::showMemoryDialog
            ).addCondition(() -> getMessagesStorage().fileProtectionEnabled()),
            new DataItem(this, "Account num", () -> Integer.toString(currentAccount)),
            new ToggleItem(this, "Clear logs with cache", TesterSettings.clearLogsWithCache),
            new ToggleItem(this, "Force search during deletion", TesterSettings.forceSearchDuringDeletion),
            new HeaderItem(this, "Pitch Factor"),
            new SeekBarItem(this, TesterSettings.pitchFactor),
            new HeaderItem(this, "Time Stretch Factor"),
            new SeekBarItem(this, TesterSettings.timeStretchFactor),
            new EditableDataItem(this, "Spectrum Distortion Params", TesterSettings.spectrumDistorterParams),
            new EditableDataItem(this, "Time Distortion Params", TesterSettings.timeDistortionParams),
            new HeaderItem(this, "World F0 Shift"),
            new SeekBarItem(this, TesterSettings.f0Shift),
            new HeaderItem(this, "World Formant Ratio"),
            new SeekBarItem(this, TesterSettings.formantRatio),
            new ToggleItem(this, "More Timer Values", TesterSettings.moreTimerValues),
    };

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

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int rowCount;

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
            for (Item item : items) {
                if (item.positionMatch(position)) {
                    item.onClick(view);
                    break;
                }
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
        for (Item item : items) {
            if (item.needAddRow()) {
                item.setPosition(rowCount++);
            }
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

    private List<TLRPC.Dialog> getAllDialogs() {
        return Utils.getAllDialogs(currentAccount);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            for (Item item : items) {
                if (item.positionMatch(holder.getAdapterPosition())) {
                    return item.enabled();
                }
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
            if (viewType == ViewTypes.TOGGLE.ordinal()) {
                view = new TextCheckCell(mContext);
            } else if (viewType == ViewTypes.BUTTON.ordinal()) {
                view = new TextSettingsCell(mContext);
            } else if (viewType == ViewTypes.HEADER.ordinal()) {
                view = new HeaderCell(mContext);
            } else if (viewType == ViewTypes.SEEK_BAR.ordinal()) {
                view = new SeekBarCell(mContext);
            } else {
                throw new RuntimeException("Unknown view type");
            }
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            for (Item item : items) {
                if (item.positionMatch(position)) {
                    item.onBindViewHolder(holder, position);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            for (Item item : items) {
                if (item.positionMatch(position)) {
                    return item.getViewType();
                }
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

