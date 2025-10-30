package org.telegram.messenger.partisan.voicechange;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SimpleRadioButtonCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VoiceChangeSettingsFragment extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private AudioRecord audioRecorder = null;
    private VoiceChanger voiceChanger = null;
    private final DispatchQueue recordQueue = new DispatchQueue("recordQueue");
    private ByteArrayOutputStream changedOutputAudioBuffer = null;
    private ByteArrayOutputStream originalOutputAudioBuffer = null;

    private final VoiceChangeExamplePlayer changedPlayer = new VoiceChangeExamplePlayer();
    private final VoiceChangeExamplePlayer originalPlayer = new VoiceChangeExamplePlayer();

    private static final int sampleRate = 48000;
    private static final int recordBufferSize = 1280;

    private String benchmarkRatioText = null;

    private int rowCount;

    private int enableRow = -1;
    private int enableDescriptionRow = -1;
    private int changeLevelHeaderRow = -1;
    private int aggressiveChangeLevelRow = -1;
    private int aggressiveChangeLevelDescriptionRow = -1;
    private int moderateChangeLevelRow = -1;
    private int moderateChangeLevelDescriptionRow = -1;
    private int generateNewParametersRow = -1;
    private int generateNewParametersDescriptionRow = -1;
    private int checkVoiceChangingHeaderRow = -1;
    private int recordRow = -1;
    private int playChangedRow = -1;
    private int playOriginalRow = -1;
    private int checkVoiceChangingDelimiterRow = -1;
    private int showVoiceChangedNotificationRow = -1;
    private int showVoiceChangedNotificationDelimiterRow = -1;
    private int enableForIndividualAccountsRow = -1;
    private int enableForIndividualAccountsDelimiterRow = -1;
    private int enableForVoiceMessagesRow = -1;
    private int enableForVideoMessagesRow = -1;
    private int enableForCallsRow = -1;
    private int enableForTypesDelimiterRow = -1;
    private int benchmarkRow = -1;

    private TextCell recordCell = null;
    private TextSettingsCell benchmarkCell = null;

    private final Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecorder != null) {
                VoiceChanger voiceChanger = VoiceChangeSettingsFragment.this.voiceChanger;
                ByteBuffer buffer = allocateByteBuffer(recordBufferSize);
                int len = audioRecorder.read(buffer, buffer.capacity());

                if (len > 0) {
                    writeToOutputBuffer(originalOutputAudioBuffer, buffer, len);
                }

                ByteBuffer changedBuffer = null;
                if (voiceChanger != null) {
                    if (len > 0) {
                        byte[] byteArray = java.util.Arrays.copyOf(buffer.array(), len);
                        voiceChanger.write(byteArray);
                    }
                    byte[] changedVoice = voiceChanger.readAll();
                    if (changedVoice.length == 0) {
                        recordQueue.postRunnable(recordRunnable);
                        return;
                    }
                    len = changedVoice.length;
                    changedBuffer = allocateByteBuffer(len);
                    changedBuffer.put(changedVoice, 0, len);
                    changedBuffer.rewind();
                }
                if (changedBuffer != null) {
                    writeToOutputBuffer(changedOutputAudioBuffer, changedBuffer, len);
                    recordQueue.postRunnable(recordRunnable);
                }
                if (originalOutputAudioBuffer.size() >= 60 * sampleRate) {
                    stopRecording();
                }
            }
        }

        private ByteBuffer allocateByteBuffer(int length) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(length);
            buffer.order(ByteOrder.nativeOrder());
            buffer.rewind();
            return buffer;
        }

        private void writeToOutputBuffer(ByteArrayOutputStream outputBuffer, ByteBuffer buffer, int len) {
            try {
                byte[] byteArray = java.util.Arrays.copyOf(buffer.array(), len);
                outputBuffer.write(byteArray);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public VoiceChangeSettingsFragment() {
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

        actionBar.setTitle(getString(R.string.VoiceChange));
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
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (!view.isEnabled()) {
                return;
            }
            if (position == enableRow) {
                boolean newValue = VoiceChangeSettings.voiceChangeEnabled.toggle();
                if (VoiceChangeSettings.areSettingsEmpty()) {
                    VoiceChangeSettings.generateNewParameters();
                }
                ((TextCheckCell)view).setChecked(newValue);
                listAdapter.notifyDataSetChanged();
            } else if (position == aggressiveChangeLevelRow) {
                if (!VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true)) {
                    VoiceChangeSettings.aggressiveChangeLevel.set(true);
                    VoiceChangeSettings.generateNewParameters();
                    listAdapter.notifyItemChanged(aggressiveChangeLevelRow);
                    listAdapter.notifyItemChanged(moderateChangeLevelRow);
                }
            } else if (position == moderateChangeLevelRow) {
                if (VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true)) {
                    VoiceChangeSettings.aggressiveChangeLevel.set(false);
                    VoiceChangeSettings.generateNewParameters();
                    listAdapter.notifyItemChanged(aggressiveChangeLevelRow);
                    listAdapter.notifyItemChanged(moderateChangeLevelRow);
                }
            } else if (position == generateNewParametersRow) {
                VoiceChangeSettings.generateNewParameters();
                Toast.makeText(getContext(), getString(R.string.VoiceChanged), Toast.LENGTH_SHORT).show();
            } else if (position == recordRow) {
                if (audioRecorder == null) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    changedPlayer.stopPlaying();
                    originalPlayer.stopPlaying();
                    if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                        return;
                    }
                    startRecording();
                    ((TextCell)view).setTextAndIcon(getString(R.string.Stop), R.drawable.quantum_ic_stop_white_24, true);
                } else {
                    stopRecording();
                    ((TextCell)view).setTextAndIcon(getString(R.string.RecordVoiceChangeExample), R.drawable.input_mic, true);
                }
            } else if (position == playChangedRow) {
                onPlayerButtonClicked(view, true);
            } else if (position == playOriginalRow) {
                onPlayerButtonClicked(view, false);
            } else if (position == showVoiceChangedNotificationRow) {
                boolean newValue = VoiceChangeSettings.showVoiceChangedNotification.toggle();
                ((TextCheckCell)view).setChecked(newValue);
            } else if (position == enableForIndividualAccountsRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                final Set<Integer> selectedAccounts = new HashSet<>(getVoiceChangeEnabledAccounts());
                LinearLayout accountsLayout = Utils.createAccountsCheckboxLayout(getContext(), selectedAccounts::contains, (acc, enabled) -> {
                    if (enabled) {
                        selectedAccounts.add(acc);
                    } else {
                        selectedAccounts.remove(acc);
                    }
                });

                builder.setTitle(LocaleController.getString(R.string.EnableForIndividualAccounts));
                builder.setView(accountsLayout);
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                    for (int accountNum : Utils.getActivatedAccountsSortedByLoginTime()) {
                        UserConfig.getInstance(accountNum).voiceChangeEnabled = selectedAccounts.contains(accountNum);
                        UserConfig.getInstance(accountNum).saveConfig(false);
                    }
                    listAdapter.notifyItemChanged(enableForIndividualAccountsRow);
                });
                showDialog(builder.create());
            } else if (position == enableForVoiceMessagesRow) {
                boolean newValue = VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.VOICE_MESSAGE);
                ((TextCheckCell)view).setChecked(newValue);
            } else if (position == enableForVideoMessagesRow) {
                boolean newValue = VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.VIDEO_MESSAGE);
                ((TextCheckCell)view).setChecked(newValue);
            } else if (position == enableForCallsRow) {
                boolean newValue = VoiceChangeSettings.toggleVoiceChangeType(VoiceChangeType.CALL);
                ((TextCheckCell)view).setChecked(newValue);
            } else if (position == benchmarkRow) {
                runBenchmark();
            }
        });

        return fragmentView;
    }

    private void onPlayerButtonClicked(View view, boolean changed) {
        if (audioRecorder != null) {
            stopRecording();
            AndroidUtilities.runOnUIThread(() -> onPlayerButtonClicked(view, changed), 100);
        }
        VoiceChangeExamplePlayer player = changed ? changedPlayer : originalPlayer;
        ByteArrayOutputStream buffer = changed ? changedOutputAudioBuffer : originalOutputAudioBuffer;
        if (!player.isPlaying()) {
            if (changed) {
                originalPlayer.stopPlaying();
            } else {
                changedPlayer.stopPlaying();
            }

            if (buffer != null) {
                byte[] audioBytes = buffer.toByteArray();
                player.startPlaying(audioBytes, () -> onPlayingFinished(changed ? playChangedRow : playOriginalRow));
                ((TextCell)view).setTextAndIcon(getString(R.string.Stop), R.drawable.quantum_ic_stop_white_24, true);
            }
        } else {
            player.stopPlaying();
        }
    }

    private void onPlayingFinished(int row) {
        AndroidUtilities.runOnUIThread(() -> listAdapter.notifyItemChanged(row));
    }

    private void startRecording() {
        try {
            changedOutputAudioBuffer = new ByteArrayOutputStream();
            originalOutputAudioBuffer = new ByteArrayOutputStream();
            audioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize);
            audioRecorder.startRecording();
            voiceChanger = new VoiceChanger(audioRecorder.getSampleRate(), VoiceChangeType.VOICE_MESSAGE);
            voiceChanger.setFinishedCallback(() -> recordQueue.postRunnable(this::stopRecordingInternal));
            recordQueue.postRunnable(recordRunnable);
        } catch (Exception e) {
            PartisanLog.e(e);
            try {
                audioRecorder.release();
                audioRecorder = null;
            } catch (Exception e2) {
                PartisanLog.e(e2);
            }
        }
    }

    private void stopRecording() {
        recordQueue.postRunnable(() -> {
            try {
                if (audioRecorder != null) {
                    if (audioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecorder.stop();
                    }
                }
                if (voiceChanger != null) {
                    voiceChanger.notifyWritingFinished();
                }
            } catch (Exception e) {
                PartisanLog.e(e);
            }
        });
    }

    private void stopRecordingInternal() {
        try {
            if (audioRecorder != null) {
                audioRecorder.release();
                audioRecorder = null;
                AndroidUtilities.runOnUIThread(() -> {
                    if (recordCell != null) {
                        recordCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    listAdapter.notifyDataSetChanged();
                });
            }
            if (voiceChanger != null) {
                voiceChanger = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void runBenchmark() {
        if (originalOutputAudioBuffer == null || originalOutputAudioBuffer.size() == 0) {
            return;
        }
        if (benchmarkCell != null) {
            benchmarkCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        final long startTime = System.currentTimeMillis();
        VoiceChanger benchmarkVoiceChanger = new VoiceChanger(sampleRate, VoiceChangeType.VOICE_MESSAGE);
        benchmarkVoiceChanger.setFinishedCallback(() -> AndroidUtilities.runOnUIThread(() -> onBenchmarkFinished(startTime)));
        benchmarkVoiceChanger.write(originalOutputAudioBuffer.toByteArray());
        benchmarkVoiceChanger.notifyWritingFinished();
    }

    private void onBenchmarkFinished(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        benchmarkRatioText = getBenchmarkRatioText(duration);
        Toast.makeText(getContext(), "Ratio: " + benchmarkRatioText, Toast.LENGTH_LONG).show();
        listAdapter.notifyItemChanged(benchmarkRow);
        if (benchmarkCell != null) {
            benchmarkCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private String getBenchmarkRatioText(long duration) {
        final int sampleSize = 2;
        double originalLength = ((double)originalOutputAudioBuffer.size() / sampleSize / sampleRate);
        double formantRatio = duration / (originalLength * 1E3);
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(formantRatio * 100) + "%";
    }

    private List<Integer> getVoiceChangeEnabledAccounts() {
        return Utils.getActivatedAccountsSortedByLoginTime().stream()
                .filter(a -> UserConfig.getInstance(a).voiceChangeEnabled)
                .collect(Collectors.toList());
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

        enableRow = rowCount++;
        enableDescriptionRow = rowCount++;
        changeLevelHeaderRow = rowCount++;
        aggressiveChangeLevelRow = rowCount++;
        aggressiveChangeLevelDescriptionRow = rowCount++;
        moderateChangeLevelRow = rowCount++;
        moderateChangeLevelDescriptionRow = rowCount++;
        generateNewParametersRow = rowCount++;
        generateNewParametersDescriptionRow = rowCount++;
        checkVoiceChangingHeaderRow = rowCount++;
        recordRow = rowCount++;
        playChangedRow = rowCount++;
        playOriginalRow = rowCount++;
        checkVoiceChangingDelimiterRow = rowCount++;
        showVoiceChangedNotificationRow = rowCount++;
        showVoiceChangedNotificationDelimiterRow = rowCount++;
        enableForIndividualAccountsRow = rowCount++;
        enableForIndividualAccountsDelimiterRow = rowCount++;
        enableForVoiceMessagesRow = rowCount++;
        enableForVideoMessagesRow = rowCount++;
        enableForCallsRow = rowCount++;
        if (VoiceChangeSettings.showBenchmarkButton.get().orElse(false)) {
            enableForTypesDelimiterRow = rowCount++;
            benchmarkRow = rowCount++;
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

    private enum ViewType {
        CHECK,
        RADIO_BUTTON,
        SETTING,
        BUTTON_WITH_ICON,
        DESCRIPTION,
        HEADER,
        DELIMITER
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == aggressiveChangeLevelRow || position == moderateChangeLevelRow
                    || position == generateNewParametersRow || position == recordRow
                    || position == playChangedRow || position == playOriginalRow
                    || position == showVoiceChangedNotificationRow || position == enableForIndividualAccountsRow
                    || position == enableForVoiceMessagesRow || position == enableForVideoMessagesRow
                    || position == enableForCallsRow || position == benchmarkRow) {
                boolean voiceChangeEnabled = VoiceChangeSettings.voiceChangeEnabled.get().orElse(false);
                if (position == playChangedRow) {
                    return voiceChangeEnabled && changedOutputAudioBuffer != null;
                } else if (position == playOriginalRow || position == benchmarkRow) {
                    return voiceChangeEnabled && originalOutputAudioBuffer != null;
                } else {
                    return voiceChangeEnabled;
                }
            }
            return holder.getItemViewType() != ViewType.DESCRIPTION.ordinal()
                    && holder.getItemViewType() != ViewType.HEADER.ordinal()
                    && holder.getItemViewType() != ViewType.DELIMITER.ordinal();
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (ViewType.values()[viewType]) {
                case CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case RADIO_BUTTON:
                    view = new SimpleRadioButtonCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case SETTING:
                    view = new TextSettingsCell(mContext);
                    ((TextSettingsCell)view).setCanDisable(true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case BUTTON_WITH_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case DESCRIPTION:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case DELIMITER:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (ViewType.values()[holder.getItemViewType()]) {
                case CHECK: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == enableRow) {
                        textCell.setTextAndCheck(getString(R.string.Enable), VoiceChangeSettings.voiceChangeEnabled.get().orElse(false), true);
                    } else if (position == showVoiceChangedNotificationRow) {
                        textCell.setTextAndCheck(getString(R.string.ShowVoiceChangedNotification), VoiceChangeSettings.showVoiceChangedNotification.get().orElse(false), true);
                    } else if (position == enableForVoiceMessagesRow) {
                        textCell.setTextAndCheck(getString(R.string.EnableForVoiceMessages), VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.VOICE_MESSAGE), true);
                    } else if (position == enableForVideoMessagesRow) {
                        textCell.setTextAndCheck(getString(R.string.EnableForVideoMessages), VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.VIDEO_MESSAGE), true);
                    } else if (position == enableForCallsRow) {
                        textCell.setTextAndCheck(getString(R.string.EnableForVideoCalls), VoiceChangeSettings.isVoiceChangeTypeEnabled(VoiceChangeType.CALL), true);
                    }
                    textCell.setEnabled(isEnabled(holder), null);
                    break;
                }
                case RADIO_BUTTON: {
                    SimpleRadioButtonCell textCell = (SimpleRadioButtonCell) holder.itemView;
                    if (position == aggressiveChangeLevelRow) {
                        textCell.setTextAndValue(getString(R.string.AggressiveVoiceChange), false, VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true));
                    } else if (position == moderateChangeLevelRow) {
                        textCell.setTextAndValue(getString(R.string.ModerateVoiceChange), false, !VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true));
                    }
                    break;
                }
                case SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == enableForIndividualAccountsRow) {
                        int enabledCount = getVoiceChangeEnabledAccounts().size();
                        String value = enabledCount == UserConfig.getActivatedAccountsCount()
                                ? getString(R.string.FilterAllChatsShort)
                                : enabledCount + "/" + UserConfig.getActivatedAccountsCount();
                        textCell.setTextAndValue(getString(R.string.EnableForIndividualAccounts), value, false);
                    } else if (position == benchmarkRow) {
                        benchmarkCell = textCell;
                        if (benchmarkRatioText != null) {
                            textCell.setTextAndValue("Benchmark", benchmarkRatioText, false);
                        } else {
                            textCell.setText("Benchmark", false);
                        }
                    }
                    break;
                }
                case BUTTON_WITH_ICON: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == generateNewParametersRow) {
                        textCell.setTextAndIcon(getString(R.string.GenerateNewVoiceChangeParameters), R.drawable.quantum_ic_refresh_white_24, false);
                    } else if (position == recordRow) {
                        recordCell = textCell;
                        textCell.setTextAndIcon(getString(R.string.RecordVoiceChangeExample), R.drawable.input_mic, true);
                    } else if (position == playChangedRow) {
                        textCell.setTextAndIcon(getString(R.string.PlayChangedVoice), R.drawable.quantum_ic_play_arrow_white_24, true);
                    } else if (position == playOriginalRow) {
                        textCell.setTextAndIcon(getString(R.string.PlayNormalVoice), R.drawable.quantum_ic_play_arrow_white_24, false);
                    }
                    break;
                }
                case DESCRIPTION: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableDescriptionRow) {
                        cell.setText(getString(R.string.VoiceChangeDescription));
                    } else if (position == aggressiveChangeLevelDescriptionRow) {
                        cell.setText(getString(R.string.AggressiveVoiceChangeDescription));
                    } else if (position == moderateChangeLevelDescriptionRow) {
                        cell.setText(getString(R.string.ModerateVoiceChangeDescription));
                    } else if (position == generateNewParametersDescriptionRow) {
                        cell.setText(getString(R.string.GenerateNewVoiceChangeParametersDescription));
                    }
                    break;
                }
                case HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setHeight(46);
                    if (position == changeLevelHeaderRow) {
                        cell.setText(getString(R.string.VoiceChangeLevel));
                    } else if (position == checkVoiceChangingHeaderRow) {
                        cell.setText(getString(R.string.CheckVoiceChanging));
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == ViewType.SETTING.ordinal()) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                textCell.setEnabled(isEnabled(holder));
            } else if (holder.getItemViewType() == ViewType.BUTTON_WITH_ICON.ordinal()) {
                TextCell textCell = (TextCell) holder.itemView;
                textCell.setEnabled(isEnabled(holder));
                textCell.showEnabledAlpha(!isEnabled(holder));
            }
        }

        @Override
        public int getItemViewType(int position) {
            return getItemViewTypeInternal(position).ordinal();
        }

        private ViewType getItemViewTypeInternal(int position) {
            if (position == enableRow || position == showVoiceChangedNotificationRow
                    || position == enableForVoiceMessagesRow || position == enableForVideoMessagesRow
                    || position == enableForCallsRow) {
                return ViewType.CHECK;
            } else if (position == aggressiveChangeLevelRow || position == moderateChangeLevelRow) {
                return ViewType.RADIO_BUTTON;
            } else if (position == enableForIndividualAccountsRow || position == benchmarkRow) {
                return ViewType.SETTING;
            } else if (position == generateNewParametersRow || position == recordRow || position == playChangedRow
                    || position == playOriginalRow) {
                return ViewType.BUTTON_WITH_ICON;
            } else if (position == enableDescriptionRow || position == aggressiveChangeLevelDescriptionRow
                    || position == moderateChangeLevelDescriptionRow|| position == generateNewParametersDescriptionRow) {
                return ViewType.DESCRIPTION;
            } else if (position == changeLevelHeaderRow || position == checkVoiceChangingHeaderRow) {
                return ViewType.HEADER;
            } else if (position == checkVoiceChangingDelimiterRow || position == showVoiceChangedNotificationDelimiterRow
                    || position == enableForIndividualAccountsDelimiterRow || position == enableForTypesDelimiterRow) {
                return ViewType.DELIMITER;
            }
            throw new RuntimeException("Unknown row: " + position);
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
