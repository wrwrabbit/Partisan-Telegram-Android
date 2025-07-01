package org.telegram.messenger.partisan.voicechange;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

class BufferRestorer {
    private final AudioDispatcher dispatcher;
    private float[] bufferTemp;

    public BufferRestorer(AudioDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public AudioProcessor createPreProcessor() {
        return new AudioProcessor() {
            @Override
            public void processingFinished() {

            }

            @Override
            public boolean process(AudioEvent audioEvent) {
                bufferTemp = audioEvent.getFloatBuffer();
                return true;
            }
        };
    }

    public AudioProcessor createPostProcessor() {
        return new AudioProcessor() {

            @Override
            public void processingFinished() {
            }

            @Override
            public boolean process(AudioEvent audioEvent) {
                dispatcher.setStepSizeAndOverlap(Constants.bufferSize, Constants.bufferOverlap);
                dispatcher.setAudioFloatBuffer(bufferTemp);
                audioEvent.setFloatBuffer(bufferTemp);
                audioEvent.setOverlap(Constants.bufferOverlap);
                return true;
            }
        };
    }
}
