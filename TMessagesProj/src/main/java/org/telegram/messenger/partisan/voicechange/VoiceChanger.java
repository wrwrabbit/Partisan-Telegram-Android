package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.partisan.voicechange.voiceprocessors.AudioSaverProcessor;
import org.telegram.messenger.partisan.voicechange.voiceprocessors.ChainedAudioProcessor;
import org.telegram.messenger.partisan.voicechange.voiceprocessors.CombinedWorldProcessor;
import org.telegram.messenger.partisan.voicechange.voiceprocessors.CombinedSpectrumProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VoiceChanger {
    private final int sampleRate;
    private final VoiceChangePipedOutputStream initialOutputStream;
    private VoiceChangePipedInputStream currentInputStream;
    private final List<AbstractDispatcherNode> dispatcherNodes = new ArrayList<>();
    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    protected final AudioSaverProcessor audioSaver;
    private boolean writingFinished = false;
    private boolean voiceChangingFinished = false;

    private final ParametersProvider parametersProvider;
    private Runnable stopCallback;
    private Runnable finishedCallback;

    public VoiceChanger(ParametersProvider parametersProvider, int sampleRate) {
        this.parametersProvider = parametersProvider;
        this.sampleRate = sampleRate;
        this.initialOutputStream = new VoiceChangePipedOutputStream();
        audioSaver = new AudioSaverProcessor();

        try {
            buildDispatcherChain();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void buildDispatcherChain() throws IOException {
        addIntermediateDispatcherNodesToChain();
        dispatcherNodes.add(createFinalOutputNode(currentInputStream));
    }

    protected void addIntermediateDispatcherNodesToChain() throws IOException {
        if (parametersProvider.formantShiftingEnabled()) {
            CombinedWorldProcessor worldProcessor = new CombinedWorldProcessor(parametersProvider, sampleRate);
            addIntermediateDispatcherNode(
                    worldProcessor,
                    worldProcessor.bufferSize,
                    worldProcessor.bufferOverlap
            );
        } else {
            addIntermediateDispatcherNode(new CombinedSpectrumProcessor(parametersProvider, sampleRate));
        }
    }

    protected void addIntermediateDispatcherNode(ChainedAudioProcessor processor) throws IOException {
        addIntermediateDispatcherNode(processor, Constants.defaultBufferSize, Constants.defaultBufferOverlap);
    }

    protected void addIntermediateDispatcherNode(ChainedAudioProcessor processor, int bufferSize, int bufferOverlap) throws IOException {
        if (currentInputStream == null) {
            currentInputStream = new VoiceChangePipedInputStream(initialOutputStream);
        }
        IntermediateDispatcherNode node = new IntermediateDispatcherNode(
                currentInputStream,
                sampleRate,
                bufferSize,
                bufferOverlap
        );
        node.addProcessor(processor);
        dispatcherNodes.add(node);

        currentInputStream = node.createConnectedInputStream();
    }

    private FinalDispatcherNode createFinalOutputNode(VoiceChangePipedInputStream inputStream) {
        FinalDispatcherNode finalNode = new FinalDispatcherNode(
                inputStream,
                sampleRate,
                Constants.defaultBufferSize,
                Constants.defaultBufferOverlap
        );

        finalNode.addProcessor(audioSaver);

        finalNode.addProcessor(
                new ChainedAudioProcessor() {
                    @Override
                    public void processingFinishedInternal() {
                        stop();
                        voiceChangingFinished = true;
                        if (finishedCallback != null) {
                            finishedCallback.run();
                        }
                    }
                }
        );

        return finalNode;
    }

    public void write(byte[] data) {
        if (writingFinished) {
            return;
        }
        for (AbstractDispatcherNode node : dispatcherNodes) {
            node.startThreadIfNotStarted();
        }

        final byte[] dataFinal = data.clone();
        threadPoolExecutor.execute(() -> {
            try {
                if (writingFinished) {
                    return;
                }
                initialOutputStream.write(dataFinal);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void notifyWritingFinished() {
        if (writingFinished) {
            return;
        }
        threadPoolExecutor.execute(() -> {
            try {
                writingFinished = true;
                initialOutputStream.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void forceStop() {
        notifyWritingFinished();
        threadPoolExecutor.execute(() -> {
            try {
                for (AbstractDispatcherNode dispatcherNode : dispatcherNodes) {
                    dispatcherNode.forceStopDispatcher();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isWritingFinished() {
        return writingFinished;
    }

    public boolean isVoiceChangingFinished() {
        return voiceChangingFinished;
    }

    public byte[] readAll() {
        return audioSaver.getAndResetByteArray();
    }

    private void stop() {
        try {
            stopDispatchers();
            initialOutputStream.close();
            threadPoolExecutor.shutdown();
            if (stopCallback != null) {
                stopCallback.run();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stopDispatchers() throws InterruptedException, IOException {
        for (int i = dispatcherNodes.size() - 1; i >= 0; i--) {
            dispatcherNodes.get(i).stop();
        }
    }

    public void setStopCallback(Runnable stopCallback) {
        this.stopCallback = stopCallback;
    }

    public void setFinishedCallback(Runnable finishedCallback) {
        this.finishedCallback = finishedCallback;
    }
}
