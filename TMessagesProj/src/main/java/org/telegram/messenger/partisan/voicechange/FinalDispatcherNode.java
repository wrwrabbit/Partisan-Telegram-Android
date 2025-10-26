package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.partisan.voicechange.voiceprocessors.ChainedAudioProcessor;

import java.io.InputStream;

public class FinalDispatcherNode extends AbstractDispatcherNode {
    public FinalDispatcherNode(InputStream inputStream, int sampleRate, int bufferSize, int bufferOverlap) {
        super(inputStream, sampleRate, bufferSize, bufferOverlap);
    }

    @Override
    public void addProcessor(ChainedAudioProcessor processor) {
        dispatcher.addAudioProcessor(processor);
    }
}
