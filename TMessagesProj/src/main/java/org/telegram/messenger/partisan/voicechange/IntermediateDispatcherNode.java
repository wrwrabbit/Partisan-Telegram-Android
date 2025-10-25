package org.telegram.messenger.partisan.voicechange;

import java.io.IOException;
import java.io.InputStream;

public class IntermediateDispatcherNode extends AbstractDispatcherNode {
    private final VoiceChangePipedOutputStream outputStream;

    public IntermediateDispatcherNode(InputStream inputStream, int sampleRate, int bufferSize, int bufferOverlap) {
        super(inputStream, sampleRate, bufferSize, bufferOverlap);
        this.outputStream = new VoiceChangePipedOutputStream();
    }

    @Override
    public void addProcessor(ChainedAudioProcessor processor) {
        dispatcher.addAudioProcessor(processor);
        PipedStreamWriterProcessor writerAdapter = new PipedStreamWriterProcessor(
                outputStream,
                dispatcher.getFormat()
        );
        processor.setNextAudioProcessor(writerAdapter);
    }

    public VoiceChangePipedInputStream createConnectedInputStream() throws IOException {
        return new VoiceChangePipedInputStream(outputStream);
    }

    @Override
    protected void stopOutputStream() throws IOException {
        outputStream.close();
    }
}
