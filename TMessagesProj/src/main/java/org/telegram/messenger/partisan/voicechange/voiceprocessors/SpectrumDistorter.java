package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.partisan.voicechange.Constants;
import org.telegram.messenger.partisan.voicechange.ParametersProvider;

import java.util.Map;

public class SpectrumDistorter extends AbstractSpectrumProcessor {
    private final int sampleRate;
    private final ParametersProvider parametersProvider;

    public SpectrumDistorter(ParametersProvider parametersProvider, int sampleRate) {
        super(sampleRate, Constants.defaultBufferSize, Constants.defaultBufferOverlap);
        this.sampleRate = sampleRate;
        this.parametersProvider = parametersProvider;
    }

    @Override
    protected void processSpectrum(float[] magnitudes, float[] frequencies) {
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
