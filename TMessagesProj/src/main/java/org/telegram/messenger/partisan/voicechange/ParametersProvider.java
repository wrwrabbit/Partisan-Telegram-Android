package org.telegram.messenger.partisan.voicechange;

import java.util.List;
import java.util.Map;

interface ParametersProvider {
    double getPitchFactor();
    double getTimeStretchFactor();
    Map<Integer, Integer> getSpectrumDistortionMap(int sampleRate);
    List<TimeDistorter.DistortionInterval> getTimeDistortionList();
    double getF0Shift();
    double getFormantRatio();

    default boolean pitchShiftingEnabled() {
        return Math.abs(getPitchFactor() - 1.0) > 0.01;
    }

    default boolean timeStretchEnabled() {
        return Math.abs(getTimeStretchFactor() - 1.0) > 0.01;
    }

    default boolean spectrumDistortionEnabled() {
        return getSpectrumDistortionMap(48000) != null;
    }

    default boolean timeDistortionEnabled() {
        return getTimeDistortionList() != null;
    }

    default boolean formantShiftingEnabled() {
        return Math.abs(getF0Shift() - 1.0) > 0.01
                || Math.abs(getFormantRatio() - 1.0) > 0.01;
    }
}
