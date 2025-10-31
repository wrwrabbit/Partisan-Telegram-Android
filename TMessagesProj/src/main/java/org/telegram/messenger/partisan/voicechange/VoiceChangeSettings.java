package org.telegram.messenger.partisan.voicechange;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;

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
import java.util.function.Function;
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
    public static final BooleanSetting useSpectrumDistortion = new BooleanSetting("useSpectrumDistortion", false);
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
        if (aggressiveChangeLevel.get().orElse(true)) {
            boolean makeBadSounds = random.nextBoolean();
            if (makeBadSounds) {
                generateBadSoundsParams(random);
            } else {
                resetBadSoundsParams();
            }
            generateFormantOrSpectrumDistortionParams(makeBadSounds ? 1.3 : 1.4, 1.7);
        } else {
            resetBadSoundsParams();
            generateFormantOrSpectrumDistortionParams(1.15, 1.3);
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

    private static void generateFormantOrSpectrumDistortionParams(double min, double max) {
        if (useSpectrumDistortion.get().orElse(false)) {
            generateSpectrumDistortionParams(min, max);
        } else {
            generateFormantParams(min, max);
        }
    }

    private static void generateFormantParams(double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean decreasePitch = random.nextBoolean();
        if (decreasePitch) {
            min = 1.0 / min;
            max = 1.0 / max;
        }
        f0Shift.set((float)random.nextDouble(min, max));
        formantRatio.set((float)random.nextDouble(min, max));

        spectrumDistorterParams.set("");
    }

    private static void generateSpectrumDistortionParams(double minShift, double maxShift) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double sourceShift = random.nextDouble(0.9, 1.1);
        Function<Integer, String> makeShiftParam = src -> {
            int shiftedSrc = (int)(src * sourceShift);
            double destShift = random.nextDouble(minShift, maxShift);
            if (random.nextBoolean()) {
                destShift = 1.0 / destShift;
            }
            int dest = (int)(shiftedSrc * destShift);
            return shiftedSrc + ":" + dest;
        };

        String paramString = makeShiftParam.apply(200) + ","
                + makeShiftParam.apply(600) + ","
                + makeShiftParam.apply(2000) + ","
                + makeShiftParam.apply(6000);

        spectrumDistorterParams.set(paramString);

        f0Shift.set(1.0f);
        formantRatio.set(1.0f);
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
