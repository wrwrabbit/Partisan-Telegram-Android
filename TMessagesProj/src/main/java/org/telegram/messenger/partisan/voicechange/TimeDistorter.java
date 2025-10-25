package org.telegram.messenger.partisan.voicechange;

import java.util.AbstractMap;
import java.util.Map;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.resample.Resampler;

public class TimeDistorter extends AbstractSpectrumProcessor {
    public static class DistortionInterval {
        public double length;
        public float stretchFactor;
    }

    private final Resampler resampler = new Resampler(false,0.1,4.0);
    private final ParametersProvider parametersProvider;

    private float currentPitchFactor;

    public TimeDistorter(ParametersProvider parametersProvider, int sampleRate) {
        super(sampleRate, Constants.defaultBufferSize, Constants.defaultBufferOverlap);
        this.parametersProvider = parametersProvider;
    }

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        currentPitchFactor = 1.0f / getStretchFactor(audioEvent.getTimeStamp());
        if (/*parametersProvider.isStretchTimeWithoutChangingPitch()*/ false) {
            return super.processInternal(audioEvent);
        }

        int samplesOverlap = audioEvent.getOverlap();
        int samplesStepSize = audioEvent.getBufferSize()-audioEvent.getOverlap();
        int targetSize = (int) ((Constants.defaultBufferSize -Constants.defaultBufferOverlap) * currentPitchFactor);
        if (audioEvent.getTimeStamp() == 0) {
            samplesOverlap = 0;
            samplesStepSize = audioEvent.getBufferSize();
            targetSize = (int) (Constants.defaultBufferSize * currentPitchFactor);
        }

        float[] src = audioEvent.getFloatBuffer();
        float[] out = new float[targetSize];

        resampler.process(
                currentPitchFactor,
                src,
                samplesOverlap,
                samplesStepSize,
                false,
                out,
                0,
                targetSize
        );

        audioEvent.setFloatBuffer(out);
        audioEvent.setOverlap(0);

        return true;
    }

    @Override
    protected void processSpectrum(float[] magnitudes, float[] frequencies) {
        float[] newMagnitudes = new float[size/2];
        float[] newFrequencies = new float[size/2];
        for (int i = 0 ; i < size/2 ; i++){
            int index = (int)(i * currentPitchFactor);
            if (index < size/2) {
                newMagnitudes[index] += magnitudes[i];
                newFrequencies[index] = (float) (frequencies[i] * currentPitchFactor);
            }
        }
        System.arraycopy(newMagnitudes, 0, magnitudes, 0, magnitudes.length);
        System.arraycopy(newFrequencies, 0, frequencies, 0, frequencies.length);
    }

    private float getStretchFactor(double timeStamp) {
        Map.Entry<Integer, Double> currentIndexAndProgress = getIntervalIndexAndProgress(timeStamp);
        int currentIndex = currentIndexAndProgress.getKey();
        double progress = currentIndexAndProgress.getValue();

        DistortionInterval currentInterval = parametersProvider.getTimeDistortionList().get(currentIndex);
        DistortionInterval nextInterval = parametersProvider.getTimeDistortionList().get((currentIndex + 1) % parametersProvider.getTimeDistortionList().size());

        return (float) (currentInterval.stretchFactor * (1.0 - progress) + nextInterval.stretchFactor * progress);
    }

    private Map.Entry<Integer, Double> getIntervalIndexAndProgress(double timeStamp) {
        double periodLength = parametersProvider.getTimeDistortionList().stream().mapToDouble(i -> i.length).sum();
        double currentIntervalTime = timeStamp % periodLength;
        for (int i = 0; i < parametersProvider.getTimeDistortionList().size(); i++) {
            DistortionInterval interval = parametersProvider.getTimeDistortionList().get(i);
            if (currentIntervalTime <= interval.length) {
                return new AbstractMap.SimpleEntry<>(i, currentIntervalTime / interval.length);
            }
            currentIntervalTime -= interval.length;
        }
        return new AbstractMap.SimpleEntry<>(parametersProvider.getTimeDistortionList().size() - 1, 0.0);
    }

    @Override
    protected boolean useOldWindowRestore() {
        return true;
    }
}
