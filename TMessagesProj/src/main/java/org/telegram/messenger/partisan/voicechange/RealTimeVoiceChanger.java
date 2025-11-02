package org.telegram.messenger.partisan.voicechange;

import java.util.Arrays;

// RealTimeVoiceChanger is good for video and calls. Sometimes the audio will be sped up to eliminate lag.
// It is better not to use this class for audio messages.
public class RealTimeVoiceChanger extends VoiceChanger {
    private final int SPEEDUP_AFTER_REMAINING_PIECES = 3;
    private final double SPEEDUP_PER_REMAINING_PIECE = 0.02;

    private boolean firstPieceRead = false;
    private double additionalSpeedupFactor = 0.0;

    public RealTimeVoiceChanger(int sampleRate) {
        super(sampleRate);
    }

    @Override
    protected void addVoiceChangingProcessorsToChain() {
        super.addVoiceChangingProcessorsToChain();
        addAudioProcessorToChain(new TimeStretcher(new TesterSettingsParametersProvider() {
            @Override
            public double getTimeStretchFactor() {
                return 1.0 + additionalSpeedupFactor;
            }
        }));
    }

    public byte[] readBytesExactCount(int count) {
        if (!firstPieceRead) {
            byte[] piece = tryReadFirstPiece(count);
            firstPieceRead = piece != null;
            return piece;
        } else {
            byte[] buffer = audioSaver.getAndRemoveBytesExactCount(count);
            double remainingPiecesCount = (double)getBufferRemainingCount() / count;
            double speedupPiecesCount = remainingPiecesCount - SPEEDUP_AFTER_REMAINING_PIECES;
            additionalSpeedupFactor = Math.max(0.0, SPEEDUP_PER_REMAINING_PIECE * speedupPiecesCount);
            return buffer;
        }
    }

    // The size of the first piece of data is equal the windows size. It's too big, so we need to skip it.
    // The remaining pieces will be the size of the hop, which is much smaller.
    private byte[] tryReadFirstPiece(int count) {
        if (getBufferRemainingCount() > count) {
            byte[] skippedBuffer = readAll();
            return Arrays.copyOfRange(skippedBuffer, skippedBuffer.length - count, skippedBuffer.length);
        } else {
            return null;
        }
    }

    private int getBufferRemainingCount() {
        return audioSaver.getRemainingCount();
    }
}
