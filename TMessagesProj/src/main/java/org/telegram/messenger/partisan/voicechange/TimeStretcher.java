package org.telegram.messenger.partisan.voicechange;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.resample.Resampler;

class TimeStretcher extends ChainedAudioProcessor {
    private final Resampler resampler = new Resampler(false,0.1,4.0);
    private final ParametersProvider parametersProvider;

    public TimeStretcher(ParametersProvider parametersProvider) {
        this.parametersProvider = parametersProvider;
    }

    @Override
    public void processingFinished() {

    }

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        float pitchFactor = (float) (1.0 / parametersProvider.getTimeStretchFactor());
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
}
