package org.telegram.messenger.partisan.voicechange;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;

import org.telegram.messenger.partisan.settings.BooleanSetting;
import org.telegram.messenger.partisan.settings.FloatSetting;
import org.telegram.messenger.partisan.settings.IntSetting;
import org.telegram.messenger.partisan.settings.LongSetting;
import org.telegram.messenger.partisan.settings.Setting;
import org.telegram.messenger.partisan.settings.SettingUtils;
import org.telegram.messenger.partisan.settings.StringSetSetting;
import org.telegram.messenger.partisan.settings.StringSetting;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VoiceChangeSettings {

    public static final BooleanSetting voiceChangeEnabled = new BooleanSetting("voiceChangeEnabled", false);
    public static final BooleanSetting aggressiveChangeLevel = new BooleanSetting("aggressiveChangeLevel", true);
    public static final StringSetting spectrumDistortionParams = new StringSetting("spectrumDistortionParams", "");
    public static final FloatSetting f0Shift = new FloatSetting("f0Shift", 1.0f);
    public static final FloatSetting lowRatio = new FloatSetting("lowRatio", 1.0f);
    public static final FloatSetting midRatio = new FloatSetting("midRatio", 1.0f);
    public static final FloatSetting highRatio = new FloatSetting("highRatio", 1.0f);
    public static final FloatSetting maxFormantSpread = new FloatSetting("maxFormantSpread", 0.0f);
    public static final IntSetting badSThreshold = new IntSetting("badSThreshold", 4500);
    public static final IntSetting badSCutoff = new IntSetting("badSCutoff", 0);
    public static final IntSetting badShMinThreshold = new IntSetting("badShMinThreshold", 2000);
    public static final IntSetting badShMaxThreshold = new IntSetting("badShMaxThreshold", 4500);
    public static final IntSetting badShCutoff = new IntSetting("badShCutoff", 0);
    public static final BooleanSetting useOldWindowRestore = new BooleanSetting("useOldWindowRestore", true);
    public static final BooleanSetting showVoiceChangedNotification = new BooleanSetting("showVoiceChangedNotification", true);
    public static final StringSetSetting enabledVoiceChangeTypes = new StringSetSetting("enabledVoiceChangeTypes",
            Arrays.stream(VoiceChangeType.values()).map(Object::toString).collect(Collectors.toSet()));
    public static final BooleanSetting useSpectrumDistortion = new BooleanSetting("useSpectrumDistortion", false);
    public static final BooleanSetting formantShiftingHarvest = new BooleanSetting("formantShiftingHarvest", false);
    public static final BooleanSetting voiceChangeWorksWithFakePasscode = new BooleanSetting("voiceChangeWorksWithFakePasscode", false);
    public static final LongSetting settingsSeed = new LongSetting("settingsSeed", 0L);

    public static void loadSettings() {
        for (Setting<?> setting : getAllSettings()) {
            setting.load();
        }
    }

    private static List<Setting<?>> getAllSettings() {
        return SettingUtils.getAllSettings(VoiceChangeSettings.class);
    }

    public static boolean areSettingsEmpty() {
        return Strings.isNullOrEmpty(spectrumDistortionParams.get().orElse(""))
                && Math.abs(f0Shift.get().orElse(1.0f) - 1.0f) < 0.01f
                && Math.abs(lowRatio.get().orElse(1.0f) - 1.0f) < 0.01f
                && Math.abs(midRatio.get().orElse(1.0f) - 1.0f) < 0.01f
                && Math.abs(highRatio.get().orElse(1.0f) - 1.0f) < 0.01f
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
