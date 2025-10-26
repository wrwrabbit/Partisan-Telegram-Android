package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.partisan.voicechange.voiceprocessors.ChainedAudioProcessor;

import java.io.IOException;
import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;

abstract class AbstractDispatcherNode {
    protected final AudioDispatcher dispatcher;
    private final VoiceChangePipedInputStream inputStream;
    private final Thread dispatcherThread;

    public AbstractDispatcherNode(InputStream inputStream, int sampleRate, int bufferSize, int bufferOverlap) {
        this.inputStream = (inputStream instanceof VoiceChangePipedInputStream)
                ? (VoiceChangePipedInputStream) inputStream
                : null;

        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        this.dispatcher = new AudioDispatcher(
                new UniversalAudioInputStream(inputStream, format),
                bufferSize,
                bufferOverlap
        );

        this.dispatcherThread = createDispatcherThread();
    }

    private Thread createDispatcherThread() {
        return new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
    }

    public abstract void addProcessor(ChainedAudioProcessor processor);

    public void startThreadIfNotStarted() {
        if (dispatcherThread.getState() == Thread.State.NEW) {
            dispatcherThread.start();
        }
    }

    public void stop() throws InterruptedException, IOException {
        if (dispatcher.isStopped()) {
            return;
        }
        dispatcher.stop();
        if (inputStream != null) {
            synchronized (inputStream) {
                inputStream.close();
                stopOutputStream();
            }
        }
        dispatcherThread.join();
    }

    protected void stopOutputStream() throws IOException {

    }
}
