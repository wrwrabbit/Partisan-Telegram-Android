package org.telegram.messenger.partisan.voicechange;

import android.util.Pair;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class VoiceChangerUtils {
    private static final Map<VoiceChanger, Pair<Integer, VoiceChangeType>> runningVoiceChangers = new HashMap<>();

    public static VoiceChanger createVoiceChangerIfNeeded(int accountNum, VoiceChangeType type, int sampleRate) {
        return genericCreateVoiceChangerIfNeeded(
                accountNum,
                type,
                () -> new VoiceChanger(new CachedVoiceChangerSettingsParametersProvider(), sampleRate)
        );
    }

    public static RealTimeVoiceChanger createRealTimeVoiceChangerIfNeeded(int accountNum, VoiceChangeType type, int sampleRate) {
        return genericCreateVoiceChangerIfNeeded(
                accountNum,
                type,
                () -> new RealTimeVoiceChanger(new CachedVoiceChangerSettingsParametersProvider(), sampleRate)
        );
    }

    private static <T extends VoiceChanger> T genericCreateVoiceChangerIfNeeded(int accountNum, VoiceChangeType type, Supplier<T> constructor) {
        if (!needChangeVoice(accountNum, type)) {
            return null;
        }
        final T voiceChanger = constructor.get();
        runningVoiceChangers.put(voiceChanger, new Pair<>(accountNum, type));
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.voiceChangingStateChanged));
        voiceChanger.setStopCallback(() -> {
            runningVoiceChangers.remove(voiceChanger);
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.voiceChangingStateChanged));
        });
        return voiceChanger;
    }

    public static boolean needChangeVoice(int accountNum, VoiceChangeType type) {
        return voiceChangeEnabled(accountNum, type) && anyParameterSet();
    }

    private static boolean voiceChangeEnabled(int accountNum, VoiceChangeType type) {
        if (FakePasscodeUtils.isFakePasscodeActivated() && !VoiceChangeSettings.voiceChangeWorksWithFakePasscode.get().orElse(true)) {
            return false;
        }
        if (!VoiceChangeSettings.voiceChangeEnabled.get().orElse(false)) {
            return false;
        }
        if (type != null && !VoiceChangeSettings.isVoiceChangeTypeEnabled(type)) {
            return false;
        }
        return UserConfig.getInstance(accountNum).voiceChangeEnabledForAccount;
    }

    private static boolean anyParameterSet() {
        ParametersProvider parametersProvider = new CachedVoiceChangerSettingsParametersProvider();
        return parametersProvider.spectrumDistortionEnabled()
                || parametersProvider.formantShiftingEnabled()
                || parametersProvider.badSEnabled()
                || parametersProvider.badShEnabled();
    }

    public static boolean needShowVoiceChangeNotification(int accountNum, VoiceChangeType type) {
        return isAnyVoiceChangerRunning(accountNum, type)
                && VoiceChangeSettings.showVoiceChangedNotification.get().orElse(true);
    }

    private static boolean isAnyVoiceChangerRunning(int accountNum, VoiceChangeType type) {
        return runningVoiceChangers.values().stream()
                .anyMatch(pair -> accountNum == pair.first && type == pair.second);
    }

    public static byte[] getBytesFromByteBuffer(ByteBuffer buffer, int len) {
        byte[] byteArray = new byte[len];
        int originalPosition = buffer.position();
        buffer.position(0);
        buffer.get(byteArray, 0, len);
        buffer.position(originalPosition);
        return byteArray;
    }
}
