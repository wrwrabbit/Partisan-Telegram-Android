package org.telegram.messenger.partisan.voicechange;

import android.util.Pair;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.resample.Resampler;

public class TimeDistorter extends ChainedAudioProcessor {
    public static class DistortionInterval {
        public double length;
        public float stretchFactor;
    }

    private final Resampler resampler = new Resampler(false,0.1,4.0);
    private final ParametersProvider parametersProvider;

    public TimeDistorter(ParametersProvider parametersProvider) {
        this.parametersProvider = parametersProvider;
    }

    @Override
    public void processingFinished() {

    }

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        float pitchFactor = 1.0f / getStretchFactor(audioEvent.getTimeStamp());

        float[] src = audioEvent.getFloatBuffer();
        float[] out = new float[(int) ((Constants.bufferSize-Constants.bufferOverlap) * pitchFactor)];
        resampler.process(
                pitchFactor,
                src,
                Constants.bufferOverlap,
                Constants.bufferSize-Constants.bufferOverlap,
                false,
                out,
                0,
                out.length
        );

        audioEvent.setFloatBuffer(out);
        audioEvent.setOverlap(0);

        return true;
    }

    private float getStretchFactor(double timeStamp) {
        Pair<Integer, Double> currentIndexAndProgress = getIntervalIndexAndProgress(timeStamp);
        int currentIndex = currentIndexAndProgress.first;
        double progress = currentIndexAndProgress.second;

        DistortionInterval currentInterval = parametersProvider.getTimeDistortionList().get(currentIndex);
        DistortionInterval nextInterval = parametersProvider.getTimeDistortionList().get((currentIndex + 1) % parametersProvider.getTimeDistortionList().size());

        return (float) (currentInterval.stretchFactor * (1.0 - progress) + nextInterval.stretchFactor * progress);
    }

    private Pair<Integer, Double> getIntervalIndexAndProgress(double timeStamp) {
        double periodLength = parametersProvider.getTimeDistortionList().stream().mapToDouble(i -> i.length).sum();
        double currentIntervalTime = timeStamp % periodLength;
        for (int i = 0; i < parametersProvider.getTimeDistortionList().size(); i++) {
            DistortionInterval interval = parametersProvider.getTimeDistortionList().get(i);
            if (currentIntervalTime <= interval.length) {
                return new Pair<>(i, currentIntervalTime / interval.length);
            }
            currentIntervalTime -= interval.length;
        }
        return new Pair<>(parametersProvider.getTimeDistortionList().size() - 1, 0.0);
    }
}
