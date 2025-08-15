package org.telegram.messenger.partisan.voicechange;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.PitchShifter;

class ChainedPitchShifter extends ChainedAudioProcessor {
    private final ParametersProvider parametersProvider;
    private final PitchShifter shifter;

    public ChainedPitchShifter(ParametersProvider parametersProvider, int sampleRate) {
        this.parametersProvider = parametersProvider;
        this.shifter = new PitchShifter(parametersProvider.getPitchFactor(), sampleRate, Constants.bufferSize, Constants.bufferOverlap);
    }

    @Override
    public void processingFinished() {
        shifter.processingFinished();
    }

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        shifter.setPitchShiftFactor((float)parametersProvider.getPitchFactor());
        return shifter.process(audioEvent);
    }
}
