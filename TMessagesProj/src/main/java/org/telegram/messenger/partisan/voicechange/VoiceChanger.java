package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.partisan.PartisanLog;

import java.io.IOException;
import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.PitchShifter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;

public class VoiceChanger {
    private final int sampleRate;
    private final VoiceChangePipedInputStream pipedInputStream;
    private final VoiceChangePipedOutputStream pipedOutputStream;
    private final AudioDispatcher dispatcher;
    private final Thread thread;
    private final AudioSaverProcessor audioSaver;
    private final DispatchQueue writeQueue = new DispatchQueue("voiceChangerWriteQueue");

    private ChainedAudioProcessor lastAudioProcessorInChain;

    private static final ParametersProvider parametersProvider = new TesterSettingsParametersProvider();

    public VoiceChanger(int sampleRate) {
        this.sampleRate = sampleRate;
        pipedOutputStream = new VoiceChangePipedOutputStream();
        try {
            pipedInputStream = new VoiceChangePipedInputStream(pipedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dispatcher = createAudioDispatcher(pipedInputStream);
        audioSaver = new AudioSaverProcessor();
        if (parametersProvider.formantShiftingEnabled()) {
            addAudioProcessorToChain(new FormantShifter(parametersProvider, sampleRate));
        } else if (parametersProvider.spectrumDistortionEnabled()) {
            addAudioProcessorToChain(new SpectrumDistorter(parametersProvider, sampleRate));
        } else if (parametersProvider.pitchShiftingEnabled()) {
            addAudioProcessorToChain(new ChainedAudioProcessor() {
                private final PitchShifter shifter = new PitchShifter(parametersProvider.getPitchFactor(), sampleRate, Constants.bufferSize, Constants.bufferOverlap);
                @Override
                public void processingFinished() {
                    shifter.processingFinished();
                }

                @Override
                public boolean processInternal(AudioEvent audioEvent) {
                    shifter.setPitchShiftFactor((float)parametersProvider.getPitchFactor());
                    return shifter.process(audioEvent);
                }
            });
        }
        if (parametersProvider.timeDistortionEnabled()) {
            addAudioProcessorToChain(new TimeDistorter(parametersProvider));
        } else if (parametersProvider.timeStretchEnabled()) {
            addAudioProcessorToChain(new TimeStretcher(parametersProvider));
        }
        addAudioProcessorToChain(audioSaver);

        thread = new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
    }

    private void addAudioProcessorToChain(ChainedAudioProcessor processor) {
        if (lastAudioProcessorInChain != null) {
            lastAudioProcessorInChain.setNextAudioProcessor(processor);
        } else {
            dispatcher.addAudioProcessor(processor);
        }
        lastAudioProcessorInChain = processor;
    }

    public void write(byte[] data) {
        if (thread.getState() == Thread.State.NEW) {
            thread.start();
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

    public byte[] readBytesExactCount(int count) {
        return audioSaver.getAndRemoveBytesExactCount(count);
    }

    public void stop() {
        try {
            dispatcher.stop();
            pipedOutputStream.close();
            pipedInputStream.close();
            writeQueue.recycle();
            thread.join();
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

    public static boolean needChangeVoice() {
        return parametersProvider.pitchShiftingEnabled()
                || parametersProvider.timeStretchEnabled()
                || parametersProvider.spectrumDistortionEnabled()
                || parametersProvider.timeDistortionEnabled()
                || parametersProvider.formantShiftingEnabled();
    }
}
