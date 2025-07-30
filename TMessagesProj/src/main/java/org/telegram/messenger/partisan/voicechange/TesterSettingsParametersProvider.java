package org.telegram.messenger.partisan.voicechange;

import com.google.common.base.Strings;

import org.telegram.ui.TesterSettingsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TesterSettingsParametersProvider implements ParametersProvider {
    @Override
    public double getPitchFactor() {
        return TesterSettingsActivity.pitchFactor;
    }

    @Override
    public double getTimeStretchFactor() {
        return TesterSettingsActivity.timeStretchFactor;
    }

    @Override
    public Map<Integer, Integer> getSpectrumDistortionMap(int sampleRate) {
        Map<Integer, Integer> distortionMap = accumulateDistortionParams(
                TesterSettingsActivity.spectrumDistorterParams,
                new HashMap<>(),
                (map, distortionParts) -> {
                    int fromHz = Integer.parseInt(distortionParts[0]);
                    int toHz = Integer.parseInt(distortionParts[1]);
                    int fromIndex = (fromHz * Constants.bufferSize) / sampleRate;
                    int toIndex = (toHz * Constants.bufferSize) / sampleRate;
                    map.put(fromIndex, toIndex);
                });
        return distortionMap != null && !distortionMap.isEmpty() ? distortionMap : null;
    }

    @Override
    public List<TimeDistorter.DistortionInterval> getTimeDistortionList() {
        List<TimeDistorter.DistortionInterval> distortionMap = accumulateDistortionParams(
                TesterSettingsActivity.timeDistortionParams,
                new ArrayList<>(),
                (list, distortionParts) -> {
                    TimeDistorter.DistortionInterval interval = new TimeDistorter.DistortionInterval();
                    interval.length = Double.parseDouble(distortionParts[0]);
                    interval.stretchFactor = Float.parseFloat(distortionParts[1]);
                    list.add(interval);
                });
        return distortionMap != null && !distortionMap.isEmpty() ? distortionMap : null;
    }

    private <T> T accumulateDistortionParams(String params, T collection, BiConsumer<T, String[]> accumulationFunction) {
        if (Strings.isNullOrEmpty(params)) {
            return null;
        }
        try {
            String[] distortionStrings = params.split(",");
            for (String distortionString : distortionStrings) {
                if (Strings.isNullOrEmpty(distortionString)) {
                    return null;
                }
                String[] distortionParts = distortionString.split(":");
                if (distortionParts.length != 2) {
                    return null;
                }
                accumulationFunction.accept(collection, distortionParts);
            }
            return collection;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public double getF0Shift() {
        return TesterSettingsActivity.f0Shift;
    }

    @Override
    public double getFormantRatio() {
        return TesterSettingsActivity.formantRatio;
    }
}
