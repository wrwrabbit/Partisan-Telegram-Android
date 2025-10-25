package org.telegram.messenger.partisan.voicechange;

import java.util.stream.IntStream;

public class VoiceDefectsProcessor extends AbstractSpectrumProcessor {
    private final ParametersProvider parametersProvider;

    public VoiceDefectsProcessor(ParametersProvider parametersProvider, int sampleRate) {
        super(sampleRate, Constants.defaultBufferSize, Constants.defaultBufferOverlap);
        this.parametersProvider = parametersProvider;
    }

    @Override
    protected boolean needProcess() {
        return parametersProvider.badSEnabled() || parametersProvider.badShEnabled();
    }

    @Override
    protected void processSpectrum(float[] magnitudes, float[] frequencies) {
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
}
