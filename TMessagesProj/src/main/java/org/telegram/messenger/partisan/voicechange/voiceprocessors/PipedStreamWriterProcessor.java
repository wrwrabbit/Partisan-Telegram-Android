package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.voicechange.AudioEventWriteHelper;
import org.telegram.messenger.partisan.voicechange.VoiceChangePipedOutputStream;

import java.io.IOException;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class PipedStreamWriterProcessor implements AudioProcessor, AudioEventWriteHelper.WriteDelegate {
    private final VoiceChangePipedOutputStream outputStream;
    private final AudioEventWriteHelper writeHelper;

    public PipedStreamWriterProcessor(VoiceChangePipedOutputStream outputStream, TarsosDSPAudioFormat audioFormat) {
        this.outputStream = outputStream;
        writeHelper = new AudioEventWriteHelper(audioFormat, this);
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        writeHelper.writeAudioEventBytes(audioEvent);
        return true;
    }

    @Override
    public void writeAudioEventBuffer(byte[] buffer, int byteOverlap, int byteStepSize) {
        try {
            outputStream.write(buffer, byteOverlap, byteStepSize);
        } catch (IOException e) {
            PartisanLog.e(e);
        }
    }

    @Override
    public void processingFinished() {
        try {
            outputStream.close();
        } catch (IOException e) {
            PartisanLog.e(e);
        }
    }
}
