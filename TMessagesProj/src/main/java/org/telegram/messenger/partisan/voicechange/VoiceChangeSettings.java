package org.telegram.messenger.partisan.voicechange;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;

import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.messenger.partisan.settings.FloatSetting;
import org.telegram.messenger.partisan.settings.IntSetting;
import org.telegram.messenger.partisan.settings.Setting;
import org.telegram.messenger.partisan.settings.SettingUtils;
import org.telegram.messenger.partisan.settings.StringSetSetting;
import org.telegram.messenger.partisan.settings.StringSetting;
import org.telegram.messenger.partisan.settings.TesterSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class VoiceChangeSettings {

    public static final BooleanSetting voiceChangeEnabled = new BooleanSetting("voiceChangeEnabled", false);
    public static final BooleanSetting aggressiveChangeLevel = new BooleanSetting("aggressiveChangeLevel", true);
    public static final FloatSetting pitchFactor = new FloatSetting("pitchFactor", 1.0f);
    public static final FloatSetting timeStretchFactor = new FloatSetting("timeStretchFactor", 1.0f);
    public static final StringSetting spectrumDistorterParams = new StringSetting("spectrumDistorterParams", "");
    public static final StringSetting timeDistortionParams = new StringSetting("timeDistortionParams", "");
    public static final FloatSetting f0Shift = new FloatSetting("f0Shift", 1.0f);
    public static final FloatSetting formantRatio = new FloatSetting("formantRatio", 1.0f);
    public static final IntSetting badSCutoff = new IntSetting("badSCutoff", 0);
    public static final IntSetting badShCutoff = new IntSetting("badShCutoff", 0);
    public static final BooleanSetting showVoiceChangedNotification = new BooleanSetting("showVoiceChangedNotification", true);
    public static final StringSetSetting enabledVoiceChangeTypes = new StringSetSetting("enabledVoiceChangeTypes",
            Arrays.stream(VoiceChangeType.values()).map(Object::toString).collect(Collectors.toSet()));
    public static final BooleanSetting formantShiftingHarvest = new BooleanSetting("formantShiftingHarvest", false);
    public static final BooleanSetting showBenchmarkButton = new BooleanSetting("showBenchmarkButton", false);

    public static void loadSettings() {
        for (Setting<?> setting : getAllSettings()) {
            setting.load();
            setting.setConditionForGet(TesterSettings::areTesterSettingsActivated);
        }
    }

    public static void generateNewParameters() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean decreasePitch = random.nextBoolean();
        if (aggressiveChangeLevel.get().orElse(true)) {
            boolean makeBadSounds = random.nextBoolean();
            if (makeBadSounds) {
                generateBadSoundsParams(random);
                if (decreasePitch) {
                    generateFormantParams(0.6, 0.77);
                } else {
                    generateFormantParams(1.3, 1.7);
                }
            } else {
                resetBadSoundsParams();
                if (decreasePitch) {
                    generateFormantParams(0.6, 0.7);
                } else {
                    generateFormantParams(1.4, 1.7);
                }
            }

        } else {
            resetBadSoundsParams();
            if (decreasePitch) {
                generateFormantParams(0.77, 0.87);
            } else {
                generateFormantParams(1.15, 1.3);
            }
        }
    }

    private static void generateBadSoundsParams(ThreadLocalRandom random) {
        boolean makeBadShSound = random.nextBoolean();
        if (makeBadShSound) {
            badSCutoff.set(0);
            badShCutoff.set(random.nextInt(6000, 8000));
        } else {
            badSCutoff.set(random.nextInt(3000, 4000));
            badShCutoff.set(0);
        }
    }

    private static void resetBadSoundsParams() {
        badSCutoff.set(0);
        badShCutoff.set(0);
    }

    private static void generateFormantParams(double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        f0Shift.set((float)random.nextDouble(min, max));
        formantRatio.set((float)random.nextDouble(min, max));
    }

    private static List<Setting<?>> getAllSettings() {
        return SettingUtils.getAllSettings(VoiceChangeSettings.class);
    }

    public static boolean areSettingsEmpty() {
        return Math.abs(pitchFactor.get().orElse(1.0f) - 1.0f) < 0.01f
                && Math.abs(timeStretchFactor.get().orElse(1.0f) - 1.0f) < 0.01f
                && Strings.isNullOrEmpty(spectrumDistorterParams.get().orElse(""))
                && Strings.isNullOrEmpty(timeDistortionParams.get().orElse(""))
                && Math.abs(f0Shift.get().orElse(1.0f) - 1.0f) < 0.01f
                && Math.abs(formantRatio.get().orElse(1.0f) - 1.0f) < 0.01f
                && badSCutoff.get().orElse(0) == 0
                && badShCutoff.get().orElse(0) == 0;
    }

    public static boolean isVoiceChangeTypeEnabled(@NonNull VoiceChangeType type) {
        return VoiceChangeSettings.enabledVoiceChangeTypes.getOrDefault().contains(type.toString());
    }

    public static boolean toggleVoiceChangeType(@NonNull VoiceChangeType type) {
        Set<String> types = enabledVoiceChangeTypes.getOrDefault();
        boolean newValue;
        if (types.contains(type.toString())) {
            newValue = false;
            types.remove(type.toString());
        } else {
            newValue = true;
            types.add(type.toString());
        }
        enabledVoiceChangeTypes.set(types);
        return newValue;
    }
}
