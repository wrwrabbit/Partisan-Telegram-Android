package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.partisan.voicechange.Constants;
import org.telegram.messenger.partisan.voicechange.ParametersProvider;

import java.util.Map;
import java.util.stream.IntStream;

public class CombinedSpectrumProcessor extends AbstractSpectrumProcessor {
    private final int sampleRate;
    private final ParametersProvider parametersProvider;

    public CombinedSpectrumProcessor(ParametersProvider parametersProvider, int sampleRate) {
        super(sampleRate, Constants.defaultBufferSize, Constants.defaultBufferOverlap);
        this.sampleRate = sampleRate;
        this.parametersProvider = parametersProvider;
    }

    @Override
    protected void processSpectrum(float[] magnitudes, float[] frequencies) {
        if (!parametersProvider.formantShiftingEnabled() && (parametersProvider.badSEnabled() || parametersProvider.badShEnabled())) {
            breakSounds(magnitudes, frequencies);
        }
        if (parametersProvider.spectrumDistortionEnabled()) {
            distortSpectrum(magnitudes, frequencies);
        }
    }

    @Override
    protected boolean useOldWindowRestore() {
        return parametersProvider.useOldWindowRestore();
    }

    private void breakSounds(float[] magnitudes, float[] frequencies) {
        if (parametersProvider.badSEnabled() && breakSIfDetected(magnitudes, frequencies)) {
            return;
        }
        if (parametersProvider.badShEnabled() && breakShIfDetected(magnitudes, frequencies)) {
            return;
        }
    }

    private boolean breakSIfDetected(float[] magnitudes, float[] frequencies) {
        if (calculateCentroid(magnitudes, frequencies) > parametersProvider.getBadSThreshold()) {
            for (int i = 0; i < frequencies.length; i++) {
                if (frequencies[i] > parametersProvider.getBadSCutoff()) {
                    magnitudes[i] = 0.0f;
                }
            }
            return true;
        }
        return false;
    }

    private boolean breakShIfDetected(float[] magnitudes, float[] frequencies) {
        float centroid = calculateCentroid(magnitudes, frequencies);
        if (parametersProvider.getBadShMinThreshold() < centroid && centroid < parametersProvider.getBadShMaxThreshold()) {
            for (int i = 0; i < frequencies.length; i++) {
                if (frequencies[i] < parametersProvider.getBadShCutoff()) {
                    magnitudes[i] = 0.0f;
                }
            }
            return true;
        }
        return false;
    }

    private float calculateCentroid(float[] magnitudes, float[] frequencies) {
        double fullSum = IntStream.range(0, magnitudes.length)
                .mapToDouble(i -> frequencies[i] * Math.abs(magnitudes[i]))
                .sum();
        double magnitudesSum = IntStream.range(0, magnitudes.length)
                .mapToDouble(i -> Math.abs(magnitudes[i]))
                .sum();
        if (magnitudesSum > 0) {
            return (float)(fullSum / magnitudesSum);
        } else {
            return 0.0f;
        }
    }

    private void distortSpectrum(float[] magnitudes, float[] frequencies) {
        float[] newMagnitudes = new float[size/2];
        float[] newFrequencies = new float[size/2];

        Map<Integer, Integer> distortionMap = parametersProvider.getSpectrumDistortionMap(sampleRate);
        for (int i = 0 ; i < size/2; i++){
            int left = 0;
            int right = size/2 - 1;
            for (int src : distortionMap.keySet()) {
                if (src < i && src > left) {
                    left = src;
                }
                if (src >= i && src < right) {
                    right = src;
                }
            }
            float percentage = ((float)i - left) / ((float)right - left);
            float left_dest = distortionMap.getOrDefault(left, left);
            float right_dest = distortionMap.getOrDefault(right, right);
            int index = (int)((right_dest - left_dest) * percentage + left_dest);

            if (index < size/2) {
                newMagnitudes[index] += magnitudes[i];
                newFrequencies[index] = frequencies[index];
            }
        }
        System.arraycopy(newMagnitudes, 0, magnitudes, 0, magnitudes.length);
        System.arraycopy(newFrequencies, 0, frequencies, 0, frequencies.length);
    }
}
