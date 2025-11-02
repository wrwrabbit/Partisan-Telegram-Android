package org.telegram.messenger.partisan.voicechange;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import be.tarsos.dsp.AudioEvent;

class AudioSaverProcessor extends ChainedAudioProcessor {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    @Override
    public boolean processInternal(AudioEvent audioEvent) {
        int byteOverlap = audioEvent.getOverlap() * 2;
        int byteStepSize = audioEvent.getBufferSize() * 2 - byteOverlap;
        if (audioEvent.getTimeStamp() == 0) {
            byteOverlap = 0;
            byteStepSize = audioEvent.getBufferSize() * 2;
        }
        synchronized (this) {
            outputStream.write(audioEvent.getByteBuffer(), byteOverlap, byteStepSize);
        }
        return true;
    }

    @Override
    public void processingFinished() {

    }

    public synchronized byte[] getAndResetByteArray() {
        byte[] result = outputStream.toByteArray();
        outputStream.reset();
        return result;
    }

    public synchronized byte[] getAndRemoveBytesExactCount(int count) {
        byte[] bytes = outputStream.toByteArray();
        if (bytes.length < count) {
            return null;
        }
        byte[] result = Arrays.copyOfRange(bytes, 0 , count);
        outputStream.reset();
        outputStream.write(bytes, count, bytes.length - count);
        return result;
    }

    public synchronized int getRemainingCount() {
        return outputStream.size();
    }
}
