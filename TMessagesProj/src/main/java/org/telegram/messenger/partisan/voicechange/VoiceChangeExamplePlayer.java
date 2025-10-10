package org.telegram.messenger.partisan.voicechange;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import org.telegram.messenger.DispatchQueue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

class VoiceChangeExamplePlayer implements Runnable {
    private static final int sampleRate = 48000;

    private ByteArrayInputStream inputAudioBuffer;
    private final DispatchQueue playQueue = new DispatchQueue("VoiceChangePlayQueue");
    private int playBufferSize;
    private AudioTrack audioTrack;
    private Runnable onPlayingFinished;

    @Override
    public void run() {
        if (audioTrack != null) {
            if (inputAudioBuffer.available() > 0) {
                int bytesToWrite = Math.min(inputAudioBuffer.available(), playBufferSize);
                byte[] tempBuffer = new byte[bytesToWrite];
                try {
                    inputAudioBuffer.read(tempBuffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                audioTrack.write(tempBuffer, 0, bytesToWrite);
                playQueue.postRunnable(this);
            } else {
                stopPlaying();
            }
        }
    }

    public boolean isPlaying() {
        return audioTrack != null;
    }

    public void startPlaying(byte[] audioBytes, Runnable onPlayingFinished) {
        if (isPlaying()) {
            throw new RuntimeException("Playing is already started");
        }
        this.inputAudioBuffer = new ByteArrayInputStream(audioBytes);
        this.onPlayingFinished = onPlayingFinished;

        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        playBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (playBufferSize == AudioTrack.ERROR || playBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Invalid audio configuration");
        }

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                playBufferSize,
                AudioTrack.MODE_STREAM
        );

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioTrack initialization failed");
        }

        audioTrack.play();
        playQueue.postRunnable(this);
    }

    public void stopPlaying() {
        if (!isPlaying()) {
            return;
        }
        playQueue.postRunnable(() -> {
            if (isPlaying()) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
                inputAudioBuffer = null;
                if (onPlayingFinished != null) {
                    onPlayingFinished.run();
                    onPlayingFinished = null;
                }
            }
        });
    }
}
