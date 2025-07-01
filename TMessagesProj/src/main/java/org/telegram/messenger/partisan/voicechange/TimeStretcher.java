package org.telegram.messenger.partisan.voicechange;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.resample.Resampler;

class TimeStretcher implements AudioProcessor {
    private final AudioDispatcher dispatcher;
    private final Resampler resampler = new Resampler(false,0.1,4.0);
    private final float pitchFactor;

    public TimeStretcher(AudioDispatcher dispatcher, double timeStretchFactor) {
        this.dispatcher = dispatcher;
        this.pitchFactor = (float) (1.0 / timeStretchFactor);
    }

    @Override
    public void processingFinished() {

    }

    @Override
    public boolean process(AudioEvent audioEvent) {

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

        dispatcher.setStepSizeAndOverlap(out.length, 0);

        audioEvent.setFloatBuffer(out);
        audioEvent.setOverlap(0);

        return true;
    }
}
