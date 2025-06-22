package org.telegram.messenger.partisan.voicechange;

import org.telegram.messenger.partisan.PartisanLog;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.PitchShifter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.UniversalAudioInputStream;

public class VoiceChanger {
    public static byte[] changeVoice(byte[] data, double pitchFactor, double timeStretchFactor, int sampleRate) {
        byte[] currentData = data;
        try {
            if (Math.abs(pitchFactor - 1.0) > 0.01) {
                currentData = changePitch(currentData, pitchFactor, sampleRate);
            }
            if (Math.abs(timeStretchFactor - 1.0) > 0.01) {
                currentData = stretchTime(currentData, timeStretchFactor, sampleRate);
            }
        } catch (Exception e) {
            PartisanLog.e(e);
        }
        return currentData;
    }

    private static byte[] changePitch(byte[] data, double pitchFactor, int sampleRate) {
        AudioDispatcher dispatcher = createAudioDispatcher(data, sampleRate);
        AudioSaverProcessor audioSaver = new AudioSaverProcessor();
        dispatcher.addAudioProcessor(new PitchShifter(pitchFactor, sampleRate, Constants.bufferSize, Constants.bufferOverlap));
        dispatcher.addAudioProcessor(audioSaver);
        dispatcher.run();
        return audioSaver.getByteArray();
    }

    private static byte[] stretchTime(byte[] data, double timeStretchFactor, int sampleRate) {
        AudioDispatcher dispatcher = createAudioDispatcher(data, sampleRate);
        TimeStretcher timeStretcher = new TimeStretcher(dispatcher, timeStretchFactor, sampleRate);
        AudioSaverProcessor audioSaver = new AudioSaverProcessor();
        dispatcher.addAudioProcessor(timeStretcher.createPreProcessor());
        dispatcher.addAudioProcessor(audioSaver);
        dispatcher.addAudioProcessor(timeStretcher.createPostProcessor());
        dispatcher.run();
        return audioSaver.getByteArray();
    }

    private static AudioDispatcher createAudioDispatcher(byte[] data, int sampleRate) {
        return new AudioDispatcher(
                createAudioStream(data, sampleRate),
                Constants.bufferSize,
                Constants.bufferOverlap
        );
    }

    private static TarsosDSPAudioInputStream createAudioStream(byte[] data, int sampleRate) {
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        InputStream inputStream = new ByteArrayInputStream(data);
        return new UniversalAudioInputStream(inputStream, format);
    }
}
