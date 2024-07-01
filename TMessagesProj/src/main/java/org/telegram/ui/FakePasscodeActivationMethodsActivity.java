package org.telegram.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
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
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.EditTemplate;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FakePasscodeActivationMethodsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private final FakePasscode fakePasscode;

    private int rowCount;

    private int activationMessageRow;
    private int activationMessageDetailRow;

    private int badTriesToActivateRow;
    private int badTriesToActivateDetailRow;

    private int activateByTimerRow;
    private int activateByTimerDetailRow;

    private int fingerprintRow;
    private int fingerprintDetailRow;

    public FakePasscodeActivationMethodsActivity(FakePasscode fakePasscode) {
        super();
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

        actionBar.setTitle(LocaleController.getString(R.string.OtherActivationMethods));
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
                if (position == badTriesToActivateRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    String blockingSetting = "";
                    if (fakePasscode.replaceOriginalPasscode) {
                        blockingSetting = LocaleController.getString(R.string.ReplaceOriginalPasscode);
                    } else if (fakePasscode.passwordlessMode) {
                        blockingSetting = LocaleController.getString(R.string.PasswordlessMode);
                    }
                    builder.setMessage(LocaleController.formatString(R.string.CannotEnableSettingDescription, blockingSetting));
                    builder.setTitle(LocaleController.getString(R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                }
                return;
            }
            if (position == activationMessageRow) {
                DialogTemplate template = new DialogTemplate();
                template.type = DialogType.EDIT;
                template.title = LocaleController.getString("ActivationMessage", R.string.ActivationMessage);
                EditTemplate editTemplate = new EditTemplate(fakePasscode.activationMessage, LocaleController.getString("Message", R.string.Message), false) {
                    @Override
                    public boolean validate(View view) {
                        if (!super.validate(view)) {
                            return false;
                        }
                        EditTextCaption edit = (EditTextCaption) view;
                        String text = edit.getText().toString();
                        if (text.startsWith(" ") || text.endsWith(" ")) {
                            edit.setError(LocaleController.getString(R.string.IncorrectActivationMessageWarning));
                            return false;
                        }
                        return true;
                    }
                };
                template.addViewTemplate(editTemplate);
                template.positiveListener = views -> {
                    fakePasscode.activationMessage = ((EditTextCaption) views.get(0)).getText().toString();
                    SharedConfig.saveConfig();
                    TextSettingsCell cell = (TextSettingsCell) view;
                    String value = fakePasscode.activationMessage.isEmpty() ? LocaleController.getString("Disabled", R.string.Disabled) : fakePasscode.activationMessage;
                    cell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), value, false);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                };
                template.negativeListener = (dlg, whichButton) -> {
                    fakePasscode.activationMessage = "";
                    TextSettingsCell cell = (TextSettingsCell) view;
                    cell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), LocaleController.getString("Disabled", R.string.Disabled), false);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                };
                AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                showDialog(dialog);
            } else if (position == badTriesToActivateRow) {
                String title = LocaleController.getString("BadPasscodeTriesToActivate", R.string.BadPasscodeTriesToActivate);
                int selected = -1;
                final int[] values = new int[]{1, 2, 3, 4, 5, 7, 10, 15, 20, 25, 30};
                if (fakePasscode.badTriesToActivate == null) {
                    selected = 0;
                } else {
                    for (int i = 0; i < values.length; i++) {
                        if (fakePasscode.badTriesToActivate <= values[i]) {
                            selected = i + 1;
                            break;
                        }
                    }
                }
                String[] items = new String[values.length + 1];
                items[0] = LocaleController.getString("Disabled", R.string.Disabled);
                for (int i = 0; i < values.length; i++) {
                    items[i + 1] = String.valueOf(values[i]);
                }
                Consumer<Integer> onClicked = which -> {
                    if (which == 0) {
                        fakePasscode.badTriesToActivate = null;
                    } else {
                        fakePasscode.badTriesToActivate = values[which - 1];
                    }
                    SharedConfig.saveConfig();
                    listAdapter.notifyDataSetChanged();
                };
                AlertDialog dialog = EnumDialogBuilder.build(getParentActivity(), title, selected, items, onClicked);
                if (dialog == null) {
                    return;
                }
                showDialog(dialog);
            } else if (position == activateByTimerRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder warningBuilder = new AlertDialog.Builder(getParentActivity());
                warningBuilder.setTitle(LocaleController.getString(R.string.TimerActivationFakePasscodeWarningTitle));
                warningBuilder.setMessage(LocaleController.getString(R.string.TimerActivationFakePasscodeWarning));
                warningBuilder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog warningDialog = warningBuilder.create();
                DialogButtonWithTimer.setButton(warningDialog, AlertDialog.BUTTON_POSITIVE, LocaleController.getString(R.string.Continue), 3, (dlg, w) -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.TimerActivationDialogTitle));
                    final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                    final List<Integer> durations = Arrays.asList(null, 1, 60, 5 * 60, 15 * 60, 30 * 60, 60 * 60,
                            2 * 60 * 60, 4 * 60 * 60, 6 * 60 * 60, 8 * 60 * 60, 10 * 60 * 60, 12 * 60 * 60, 16 * 60 * 60, 24 * 60 * 60);
                    numberPicker.setMinValue(0);
                    numberPicker.setMaxValue(durations.size() - 1);
                    int index = durations.indexOf(fakePasscode.activateByTimerTime);
                    numberPicker.setValue(index != -1 ? index : 0);
                    numberPicker.setFormatter(value -> {
                        if (value == 0) {
                            return LocaleController.getString(R.string.Disabled);
                        } else {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatDuration(durations.get(value)));
                        }
                    });
                    builder.setView(numberPicker);
                    builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), (dialog, which) -> {
                        fakePasscode.activateByTimerTime = durations.get(numberPicker.getValue());
                        SharedConfig.saveConfig();
                        listAdapter.notifyItemChanged(position);
                    });
                    showDialog(builder.create());
                });
                showDialog(warningDialog);
            } else if (position == fingerprintRow) {
                TextCheckCell cell = (TextCheckCell) view;
                fakePasscode.activateByFingerprint = !fakePasscode.activateByFingerprint;
                if (fakePasscode.activateByFingerprint) {
                    fakePasscode.allowLogin = true;
                }
                SharedConfig.saveConfig();
                cell.setChecked(fakePasscode.activateByFingerprint);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
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

        activationMessageRow = rowCount++;
        activationMessageDetailRow = rowCount++;

        badTriesToActivateRow = rowCount++;
        badTriesToActivateDetailRow = rowCount++;

        activateByTimerRow = rowCount++;
        activateByTimerDetailRow = rowCount++;

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && AndroidUtilities.isKeyguardSecure() && SharedConfig.useFingerprintLock) {
                    fingerprintRow = rowCount++;
                    fingerprintDetailRow = rowCount++;
                } else {
                    fingerprintRow = -1;
                    fingerprintDetailRow = -1;
                }
            } else {
                fingerprintRow = -1;
                fingerprintDetailRow = -1;
            }
        } catch (Throwable e) {
            FileLog.e(e);
            fingerprintRow = -1;
            fingerprintDetailRow = -1;
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
            return position == activationMessageRow
                    || (position == badTriesToActivateRow && !fakePasscode.replaceOriginalPasscode && !fakePasscode.passwordlessMode)
                    || position == activateByTimerRow
                    || (position == fingerprintRow && (FakePasscodeUtils.getFingerprintFakePasscode() == null || fakePasscode.activateByFingerprint));
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
                case 3:
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
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == fingerprintRow) {
                        textCell.setTextAndCheck(LocaleController.getString(R.string.ActivateWithFingerprint), fakePasscode.activateByFingerprint, false);
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == activationMessageRow) {
                        String value = fakePasscode.activationMessage.isEmpty() ? LocaleController.getString("Disabled", R.string.Disabled) : fakePasscode.activationMessage;
                        textCell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), value, false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == badTriesToActivateRow) {
                        String value = fakePasscode.badTriesToActivate == null ? LocaleController.getString("Disabled", R.string.Disabled) : String.valueOf(fakePasscode.badTriesToActivate);
                        textCell.setTextAndValue(LocaleController.getString("BadPasscodeTriesToActivate", R.string.BadPasscodeTriesToActivate), value, false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == activateByTimerRow){
                        String val;
                        if (fakePasscode.activateByTimerTime == null) {
                            val = LocaleController.formatString("Disabled", R.string.Disabled);
                        } else {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatDuration(fakePasscode.activateByTimerTime));
                        }
                        textCell.setTextAndValue(LocaleController.getString(R.string.TimerActivationFakePasscode), val, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == activateByTimerDetailRow){
                        cell.setText(LocaleController.getString("TimerActivationFakePasscodeInfo", R.string.TimerActivationFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == activationMessageDetailRow) {
                        cell.setText(LocaleController.getString("ActivationMessageInfo", R.string.ActivationMessageInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == badTriesToActivateDetailRow) {
                        cell.setText(LocaleController.getString("BadPasscodeTriesToActivateInfo", R.string.BadPasscodeTriesToActivateInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == fingerprintDetailRow) {
                        cell.setText(LocaleController.getString(R.string.ActivateWithFingerprintInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) {
                TextCheckCell textCell = (TextCheckCell) holder.itemView;
                if (holder.getAdapterPosition() == fingerprintRow) {
                    boolean enabled = FakePasscodeUtils.getFingerprintFakePasscode() == null || fakePasscode.activateByFingerprint;
                    textCell.setEnabled(enabled, null);
                } else {
                    textCell.setEnabled(isEnabled(holder), null);
                }
            } else if (holder.getItemViewType() == 1) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (holder.getAdapterPosition() == badTriesToActivateRow) {
                    boolean enabled = !fakePasscode.replaceOriginalPasscode && !fakePasscode.passwordlessMode;
                    textCell.setEnabled(enabled, null);
                } else {
                    textCell.setEnabled(isEnabled(holder), null);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == fingerprintRow) {
                return 0;
            } else if (position == activationMessageRow || position == badTriesToActivateRow
                    || position == activateByTimerRow) {
                return 1;
            } else if (position == activationMessageDetailRow || position == badTriesToActivateDetailRow
                    || position == fingerprintDetailRow || position == activateByTimerDetailRow) {
                return 2;
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
