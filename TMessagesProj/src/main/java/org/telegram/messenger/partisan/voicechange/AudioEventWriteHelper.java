package org.telegram.messenger.partisan.voicechange;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class AudioEventWriteHelper {
    public interface WriteDelegate {
        void writeAudioEventBuffer(byte[] buffer, int byteOverlap, int byteStepSize);
    }

    private final TarsosDSPAudioFormat format;
    private final WriteDelegate delegate;

    public AudioEventWriteHelper(TarsosDSPAudioFormat format, WriteDelegate delegate) {
        this.format = format;
        this.delegate = delegate;
    }

    public void writeAudioEventBytes(AudioEvent audioEvent) {
        int byteOverlap = audioEvent.getOverlap() * format.getFrameSize();
        int byteStepSize = audioEvent.getBufferSize() * format.getFrameSize() - byteOverlap;
        if(audioEvent.getTimeStamp() == 0) {
            byteOverlap = 0;
            byteStepSize = audioEvent.getBufferSize() * format.getFrameSize();
        }

        delegate.writeAudioEventBuffer(audioEvent.getByteBuffer(), byteOverlap, byteStepSize);
    }
}
