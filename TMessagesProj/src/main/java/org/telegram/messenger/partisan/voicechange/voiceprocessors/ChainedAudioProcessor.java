package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

public abstract class ChainedAudioProcessor implements AudioProcessor {
    protected AudioProcessor nextAudioProcessor;

    public void setNextAudioProcessor(AudioProcessor nextAudioProcessor) {
        this.nextAudioProcessor = nextAudioProcessor;
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        boolean result = processInternal(audioEvent);
        if (nextAudioProcessor != null) {
            nextAudioProcessor.process(audioEvent);
        }
        return result;
    }

    protected boolean processInternal(AudioEvent audioEvent) {
        return true;
    }

    @Override
    public void processingFinished() {
        processingFinishedInternal();
        if (nextAudioProcessor != null) {
            nextAudioProcessor.processingFinished();
        }
    }

    protected void processingFinishedInternal() {}
}
