package org.telegram.messenger.partisan.voicechange;

import android.util.Pair;

import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.resample.Resampler;

public class TimeDistorter extends ChainedAudioProcessor {
    public static class DistortionInterval {
        public double length;
        public float stretchFactor;
    }

    private final Resampler resampler = new Resampler(false,0.1,4.0);
    private final List<DistortionInterval> distortionIntervalsList;
    private final double periodLength;

    public TimeDistorter(List<DistortionInterval> distortionIntervalsList) {
        this.distortionIntervalsList = distortionIntervalsList;
        this.periodLength = distortionIntervalsList.stream().mapToDouble(i -> i.length).sum();
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

        DistortionInterval currentInterval = distortionIntervalsList.get(currentIndex);
        DistortionInterval nextInterval = distortionIntervalsList.get((currentIndex + 1) % distortionIntervalsList.size());

        return (float) (currentInterval.stretchFactor * (1.0 - progress) + nextInterval.stretchFactor * progress);
    }

    private Pair<Integer, Double> getIntervalIndexAndProgress(double timeStamp) {
        double currentIntervalTime = timeStamp % periodLength;
        for (int i = 0; i < distortionIntervalsList.size(); i++) {
            DistortionInterval interval = distortionIntervalsList.get(i);
            if (currentIntervalTime <= interval.length) {
                return new Pair<>(i, currentIntervalTime / interval.length);
            }
            currentIntervalTime -= interval.length;
        }
        return new Pair<>(distortionIntervalsList.size() - 1, 0.0);
    }
}
