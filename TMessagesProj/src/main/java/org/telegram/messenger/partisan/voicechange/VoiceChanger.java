package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.ui.TesterSettingsActivity;

import java.io.IOException;
import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
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

    public VoiceChanger(double pitchFactor, double timeStretchFactor, int sampleRate) {
        this.sampleRate = sampleRate;
        pipedOutputStream = new VoiceChangePipedOutputStream();
        try {
            pipedInputStream = new VoiceChangePipedInputStream(pipedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dispatcher = createAudioDispatcher(pipedInputStream);
        BufferRestorer bufferRestorer = new BufferRestorer(dispatcher);
        audioSaver = new AudioSaverProcessor();
        dispatcher.addAudioProcessor(bufferRestorer.createPreProcessor());
        dispatcher.addAudioProcessor(new PitchShifter(pitchFactor, sampleRate, Constants.bufferSize, Constants.bufferOverlap));
        dispatcher.addAudioProcessor(new TimeStretcher(dispatcher, timeStretchFactor));
        dispatcher.addAudioProcessor(audioSaver);
        dispatcher.addAudioProcessor(bufferRestorer.createPostProcessor());

        thread = new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
    }

    public byte[] changeVoice(byte[] data) {
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

        return audioSaver.getAndResetByteArray();
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
        return Math.abs(TesterSettingsActivity.pitchFactor - 1.0) > 0.01
                || Math.abs(TesterSettingsActivity.timeStretchFactor - 1.0) > 0.01;
    }
}
