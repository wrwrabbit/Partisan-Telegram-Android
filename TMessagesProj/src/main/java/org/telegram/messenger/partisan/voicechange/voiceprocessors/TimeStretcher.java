package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.partisan.voicechange.Constants;
import org.telegram.messenger.partisan.voicechange.ParametersProvider;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.resample.Resampler;

public class TimeStretcher extends ChainedAudioProcessor {
    private final Resampler resampler = new Resampler(false, 0.1, 4.0);
    private final ParametersProvider parametersProvider;

    public TimeStretcher(ParametersProvider parametersProvider) {
        this.parametersProvider = parametersProvider;
    }

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        float pitchFactor = (float) (1.0 / parametersProvider.getTimeStretchFactor());
        float[] src = audioEvent.getFloatBuffer();
        float[] out = new float[(int) ((Constants.defaultBufferSize -Constants.defaultBufferOverlap) * pitchFactor)];
        resampler.process(
                pitchFactor,
                src,
                Constants.defaultBufferOverlap,
                Constants.defaultBufferSize -Constants.defaultBufferOverlap,
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
