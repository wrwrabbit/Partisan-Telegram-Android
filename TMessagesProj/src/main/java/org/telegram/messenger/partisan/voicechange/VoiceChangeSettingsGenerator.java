package org.telegram.messenger.partisan.voicechange;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class VoiceChangeSettingsGenerator {
    private static final int[] BASE_FREQUENCIES = new int[] {200, 600, 2000, 6000};
    public static final float MIN_AGGRESSIVE_SHIFT = 1.2f;
    public static final float MAX_AGGRESSIVE_SHIFT = 1.7f;
    public static final float MIN_MODERATE_SHIFT = 1.2f;
    public static final float MAX_MODERATE_SHIFT = 1.4f;
    private Random random;

    public void generateParameters(boolean newSeed) {
        if (newSeed) {
            generateNewSeed();
        }
        random = new Random(VoiceChangeSettings.settingsSeed.get().orElse(0L));
        if (VoiceChangeSettings.useSpectrumDistortion.get().orElse(false)) {
            generateLowQualityParams();
        } else {
            generateHighQualityParams();
        }
    }

    private static void generateNewSeed() {
        long seed = 0;
        while (seed == 0) {
            seed = ThreadLocalRandom.current().nextLong();
        }
        VoiceChangeSettings.settingsSeed.set(seed);
    }

    private void generateHighQualityParams() {
        if (VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true)) {
            boolean makeBadSounds = random.nextBoolean();
            if (makeBadSounds) {
                generateBadSoundsParams();
            } else {
                resetBadSoundsParams();
            }
            generateFormantParams(MIN_AGGRESSIVE_SHIFT, MAX_AGGRESSIVE_SHIFT);
            VoiceChangeSettings.maxFormantSpread.set(generateRandomFloat(0.15f, 0.25f));
        } else {
            resetBadSoundsParams();
            generateFormantParams(MIN_MODERATE_SHIFT, MAX_MODERATE_SHIFT);
            VoiceChangeSettings.maxFormantSpread.set(0.05f);
        }
    }

    private void generateLowQualityParams() {
        VoiceChangeSettings.maxFormantSpread.set(0.0f);
        List<Integer> frequencies = Arrays.stream(BASE_FREQUENCIES)
                .boxed()
                .collect(Collectors.toList());
        if (VoiceChangeSettings.aggressiveChangeLevel.get().orElse(true)) {
            generateBadSoundsParams();
            frequencies.remove(random.nextInt(frequencies.size()));
        } else {
            resetBadSoundsParams();
            frequencies.remove(generateRandomInt(2, 4));
            frequencies.remove(0);
        }
        generateSpectrumDistortionParams(frequencies);
    }

    private void generateBadSoundsParams() {
        boolean makeBadShSound = random.nextBoolean();
        if (makeBadShSound) {
            VoiceChangeSettings.badSCutoff.set(0);
            VoiceChangeSettings.badShCutoff.set(generateRandomInt(6000, 8000));
        } else {
            VoiceChangeSettings.badSCutoff.set(generateRandomInt(1000, 2000));
            VoiceChangeSettings.badShCutoff.set(0);
        }
    }

    private static void resetBadSoundsParams() {
        VoiceChangeSettings.badSCutoff.set(0);
        VoiceChangeSettings.badShCutoff.set(0);
    }

    private void generateFormantParams(float min, float max) {
        boolean decreasePitch = random.nextBoolean();
        if (decreasePitch) {
            float newMax = 1.0f / min;
            min = 1.0f / max;
            max = newMax;
        }
        VoiceChangeSettings.f0Shift.set(generateRandomFloat(min, max));
        VoiceChangeSettings.lowRatio.set(generateRandomFloat(min, max));
        VoiceChangeSettings.midRatio.set(generateRandomFloat(min, max));
        VoiceChangeSettings.highRatio.set(generateRandomFloat(min, max));

        VoiceChangeSettings.spectrumDistortionParams.set("");
    }

    private void generateSpectrumDistortionParams(List<Integer> frequenciesToShift) {
        StringBuilder paramString = new StringBuilder();
        boolean increaseParams = random.nextBoolean();
        int oppositeFrequency = frequenciesToShift.get(random.nextInt(frequenciesToShift.size()));
        for (int frequency : BASE_FREQUENCIES) {
            if (paramString.length() > 0) {
                paramString.append(",");
            }
            if (frequenciesToShift.contains(frequency)) {
                boolean increase = frequency == oppositeFrequency
                        ? !increaseParams
                        : increaseParams;
                paramString.append(generateSingleFrequencyShift(frequency, increase));
            } else {
                paramString.append(frequency).append(":").append(frequency);
            }
        }

        VoiceChangeSettings.spectrumDistortionParams.set(paramString.toString());

        VoiceChangeSettings.f0Shift.set(1.0f);
        VoiceChangeSettings.lowRatio.set(1.0f);
        VoiceChangeSettings.midRatio.set(1.0f);
        VoiceChangeSettings.highRatio.set(1.0f);
    }

    private String generateSingleFrequencyShift(int src, boolean increase) {
        int shiftedSrc = (int)(src * generateRandomFloat(0.9f, 1.0f));
        int shiftedDest = (int)(2 * src * generateRandomFloat(1.0f, 1.1f));
        if (increase) {
            return shiftedSrc + ":" + shiftedDest;
        } else {
            return shiftedDest + ":" + shiftedSrc;
        }
    }

    private int generateRandomInt(int origin, int bound) {
        return random.nextInt(bound - origin) + origin;
    }

    private float generateRandomFloat(float origin, float bound) {
        return random.nextFloat() * (bound - origin) + origin;
    }
}
