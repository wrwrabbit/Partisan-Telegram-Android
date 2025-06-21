package org.telegram.messenger.partisan.voicechange;

import androidx.annotation.NonNull;

import org.telegram.messenger.partisan.PartisanLog;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.PitchShifter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.UniversalAudioInputStream;

public class VoiceChanger {
    public static byte[] changeVoice(byte[] data, double factor, int sampleRate) {
        try {
            final int bufferSize = 1024;
            final int bufferOverlap = bufferSize - 128;

            AudioDispatcher dispatcher = new AudioDispatcher(createAudioStream(data, sampleRate), bufferSize, bufferOverlap);

            PitchShifter shifter = new PitchShifter(factor, sampleRate, bufferSize, bufferOverlap);
            dispatcher.addAudioProcessor(shifter);

            AudioSaverProcessor audioSaver = new AudioSaverProcessor();
            dispatcher.addAudioProcessor(audioSaver);

            dispatcher.run();
            return audioSaver.getByteArray();
        } catch (Exception e) {
            PartisanLog.e(e);
            return data;
        }
    }

    @NonNull
    private static TarsosDSPAudioInputStream createAudioStream(byte[] data, int sampleRate) {
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        InputStream inputStream = new ByteArrayInputStream(data);
        return new UniversalAudioInputStream(inputStream, format);
    }
}
