package org.telegram.messenger.partisan.voicechange;

import java.util.Map;

public class SpectrumDistorter extends AbstractSpectrumProcessor {
    private final Map<Integer, Integer> distortionMap;

    public SpectrumDistorter(Map<Integer, Integer> distortionMap, double sampleRate, int size, int overlap) {
        super(sampleRate, size, overlap);
        this.distortionMap = distortionMap;
    }

    @Override
    protected void processSpectrum(float[] currentMagnitudes, float[] currentFrequencies, float[] newMagnitudes, float[] newFrequencies) {
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
                newMagnitudes[index] += currentMagnitudes[i];
                newFrequencies[index] = currentFrequencies[index];
            }
        }
    }
}
