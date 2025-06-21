package org.telegram.messenger.partisan.voicechange;

import java.io.ByteArrayOutputStream;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

class AudioSaverProcessor implements AudioProcessor {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    @Override
    public boolean process(AudioEvent audioEvent) {
        int byteOverlap = audioEvent.getOverlap() * 2;
        int byteStepSize = audioEvent.getBufferSize() * 2 - byteOverlap;
        if(audioEvent.getTimeStamp() == 0){
            byteOverlap = 0;
            byteStepSize = audioEvent.getBufferSize() * 2;
        }
        outputStream.write(audioEvent.getByteBuffer(), byteOverlap, byteStepSize);
        return true;
    }

    @Override
    public void processingFinished() {

    }

    public byte[] getByteArray() {
        return outputStream.toByteArray();
    }
}
