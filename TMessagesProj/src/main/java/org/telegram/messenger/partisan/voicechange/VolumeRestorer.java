package org.telegram.messenger.partisan.voicechange;

import java.util.HashMap;
import java.util.Map;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.GainProcessor;

public class VolumeRestorer {
    private final Map<Integer, Double> volumes = new HashMap<>();

    public ChainedAudioProcessor createPreProcessor() {
        return new ChainedAudioProcessor() {
            private int count = 0;

            @Override
            public void processingFinished() {

            }

            @Override
            public boolean processInternal(AudioEvent audioEvent) {
                double volume = calculateVolume(audioEvent.getFloatBuffer());
                volumes.put(count, volume);
                count++;
                return true;
            }
        };
    }

    public ChainedAudioProcessor createPostProcessor() {
        return new ChainedAudioProcessor() {
            private int count = 0;

            @Override
            public void processingFinished() {
            }

            @Override
            public boolean processInternal(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                double oldVolume = volumes.remove(count);
                count++;
                double currentVolume = calculateVolume(buffer);
                GainProcessor gainProcessor = new GainProcessor(oldVolume / currentVolume);
                gainProcessor.process(audioEvent);
                return true;
            }
        };
    }

    private double calculateVolume(float[] buffer) {
        double volume = 0.0;
        for (float value : buffer) {
            volume += Math.abs(value);
        }
        volume /= buffer.length;
        return volume;
    }
}
