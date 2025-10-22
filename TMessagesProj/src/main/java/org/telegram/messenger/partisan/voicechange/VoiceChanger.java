package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;

public class VoiceChanger {
    private static final Set<VoiceChanger> runningVoiceChangers = new HashSet<>();

    private final int sampleRate;
    private final VoiceChangePipedInputStream pipedInputStream;
    private final VoiceChangePipedOutputStream pipedOutputStream;
    private final AudioDispatcher dispatcher;
    private final Thread dispatcherThread;
    protected final AudioSaverProcessor audioSaver;
    private final DispatchQueue writeQueue = new DispatchQueue("voiceChangerWriteQueue");

    private ChainedAudioProcessor lastAudioProcessorInChain;

    private static final ParametersProvider parametersProvider = new TesterSettingsParametersProvider();

    public VoiceChanger(int sampleRate) {
        this.sampleRate = sampleRate;
        pipedOutputStream = new VoiceChangePipedOutputStream();
        pipedInputStream = createInputStream(pipedOutputStream);
        dispatcher = createAudioDispatcher(pipedInputStream);
        audioSaver = new AudioSaverProcessor();
        buildAudioProcessorChain();
        dispatcherThread = createDispatcherThread();
        runningVoiceChangers.add(this);
    }

    private VoiceChangePipedInputStream createInputStream(VoiceChangePipedOutputStream pipedOutputStream) {
        try {
            return new VoiceChangePipedInputStream(pipedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void buildAudioProcessorChain() {
        VolumeRestorer volumeRestorer = new VolumeRestorer();
        addAudioProcessorToChain(volumeRestorer.createPreProcessor());

        addVoiceChangingProcessorsToChain();

        addAudioProcessorToChain(volumeRestorer.createPostProcessor());
        addAudioProcessorToChain(audioSaver);
    }

    protected void addVoiceChangingProcessorsToChain() {
        if (parametersProvider.formantShiftingEnabled()) {
            addAudioProcessorToChain(new FormantShifter(parametersProvider, sampleRate));
        } else if (parametersProvider.spectrumDistortionEnabled()) {
            addAudioProcessorToChain(new SpectrumDistorter(parametersProvider, sampleRate));
        } else if (parametersProvider.pitchShiftingEnabled()) {
            addAudioProcessorToChain(new ChainedPitchShifter(parametersProvider, sampleRate));
        }

        if (parametersProvider.timeDistortionEnabled()) {
            addAudioProcessorToChain(new TimeDistorter(parametersProvider));
        } else if (parametersProvider.timeStretchEnabled()) {
            addAudioProcessorToChain(new TimeStretcher(parametersProvider));
        }
    }

    protected void addAudioProcessorToChain(ChainedAudioProcessor processor) {
        if (lastAudioProcessorInChain != null) {
            lastAudioProcessorInChain.setNextAudioProcessor(processor);
        } else {
            dispatcher.addAudioProcessor(processor);
        }
        lastAudioProcessorInChain = processor;
    }

    private Thread createDispatcherThread() {
        return new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
    }

    public void write(byte[] data) {
        if (dispatcherThread.getState() == Thread.State.NEW) {
            dispatcherThread.start();
        }
        writeQueue.postRunnable(() -> {
            try {
                pipedOutputStream.write(data);
            } catch (Exception e) {
                PartisanLog.e(e);
            }
        });
    }

    public byte[] readAll() {
        return audioSaver.getAndResetByteArray();
    }

    public void stop() {
        try {
            dispatcher.stop();
            pipedOutputStream.close();
            pipedInputStream.close();
            writeQueue.recycle();
            dispatcherThread.join();
            runningVoiceChangers.remove(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AudioDispatcher createAudioDispatcher(InputStream inputStream) {
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        return new AudioDispatcher(
                new UniversalAudioInputStream(inputStream, format),
                Constants.bufferSize,
                Constants.bufferOverlap
        );
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
                || parametersProvider.formantShiftingEnabled();
    }

    public static boolean needShowVoiceChangeNotification() {
        return isAnyVoiceChangerRunning() && VoiceChangeSettings.showVoiceChangedNotification.get().orElse(true);
    }

    private static boolean isAnyVoiceChangerRunning() {
        return !runningVoiceChangers.isEmpty();
    }
}
