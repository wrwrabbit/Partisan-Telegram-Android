package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.UserConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VoiceChanger {
    private static final Set<VoiceChanger> runningVoiceChangers = new HashSet<>();

    private final int sampleRate;
    private final VoiceChangePipedOutputStream initialOutputStream;
    private VoiceChangePipedInputStream currentInputStream;
    private final List<AbstractDispatcherNode> dispatcherNodes = new ArrayList<>();
    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    protected final AudioSaverProcessor audioSaver;
    private boolean isWritingFinished = false;

    private static final ParametersProvider parametersProvider = new TesterSettingsParametersProvider();
    private Runnable callback;

    public VoiceChanger(int sampleRate) {
        this.sampleRate = sampleRate;
        this.initialOutputStream = new VoiceChangePipedOutputStream();
        audioSaver = new AudioSaverProcessor();

        try {
            buildDispatcherChain();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        runningVoiceChangers.add(this);
    }

    private void buildDispatcherChain() throws IOException {
        addIntermediateDispatcherNodesToChain();
        dispatcherNodes.add(createFinalOutputNode(currentInputStream));
    }

    protected void addIntermediateDispatcherNodesToChain() throws IOException {
        if (parametersProvider.formantShiftingEnabled()) {
            addIntermediateDispatcherNode(
                    new FormantShifter(parametersProvider, sampleRate),
                    FormantShifter.bufferSize,
                    FormantShifter.bufferOverlap
            );
        } else if (parametersProvider.spectrumDistortionEnabled()) {
            addIntermediateDispatcherNode(new SpectrumDistorter(parametersProvider, sampleRate));
        } else if (parametersProvider.pitchShiftingEnabled()) {
            addIntermediateDispatcherNode(new ChainedPitchShifter(parametersProvider, sampleRate));
        }

        addIntermediateDispatcherNode(new VoiceDefectsProcessor(parametersProvider, sampleRate));

        if (parametersProvider.timeDistortionEnabled()) {
            addIntermediateDispatcherNode(new TimeDistorter(parametersProvider, sampleRate));
        } else if (parametersProvider.timeStretchEnabled()) {
            addIntermediateDispatcherNode(new TimeStretcher(parametersProvider));
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
                        if (callback != null) {
                            callback.run();
                        }
                    }
                }
        );

        return finalNode;
    }

    public void write(byte[] data) {
        for (AbstractDispatcherNode node : dispatcherNodes) {
            node.startThreadIfNotStarted();
        }

        final byte[] dataFinal = data.clone();
        threadPoolExecutor.execute(() -> {
            try {
                if (isWritingFinished) {
                    return;
                }
                initialOutputStream.write(dataFinal);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void writingFinished() {
        if (isWritingFinished) {
            return;
        }
        threadPoolExecutor.execute(() -> {
            try {
                isWritingFinished = true;
                initialOutputStream.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public byte[] readAll() {
        return audioSaver.getAndResetByteArray();
    }

    private void stop() {
        try {
            stopDispatchers();
            initialOutputStream.close();
            threadPoolExecutor.shutdown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stopDispatchers() throws InterruptedException, IOException {
        for (int i = dispatcherNodes.size() - 1; i >= 0; i--) {
            dispatcherNodes.get(i).stop();
        }
    }

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    public static boolean needChangeVoice(int accountNum, VoiceChangeType type) {
        return voiceChangeEnabled(accountNum, type) && anyParameterSet();
    }

    private static boolean voiceChangeEnabled(int accountNum, VoiceChangeType type) {
        if (!VoiceChangeSettings.voiceChangeEnabled.get().orElse(false)) {
            return false;
        }
        if (type != null && !VoiceChangeSettings.isVoiceChangeTypeEnabled(type)) {
            return false;
        }
        return UserConfig.getInstance(accountNum).voiceChangeEnabled;
    }

    private static boolean anyParameterSet() {
        return parametersProvider.pitchShiftingEnabled()
                || parametersProvider.timeStretchEnabled()
                || parametersProvider.spectrumDistortionEnabled()
                || parametersProvider.timeDistortionEnabled()
                || parametersProvider.formantShiftingEnabled()
                || parametersProvider.badSEnabled()
                || parametersProvider.badShEnabled();
    }

    public static boolean needShowVoiceChangeNotification() {
        return isAnyVoiceChangerRunning() && VoiceChangeSettings.showVoiceChangedNotification.get().orElse(true);
    }

    private static boolean isAnyVoiceChangerRunning() {
        return !runningVoiceChangers.isEmpty();
    }
}
