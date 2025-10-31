package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.partisan.voicechange.ParametersProvider;
import org.telegram.messenger.partisan.voicechange.VoiceChangeSettings;
import org.telegram.messenger.partisan.voicechange.WorldUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.resample.Resampler;

public class FormantShifter extends ChainedAudioProcessor {

    public static final int bufferSize = 16 * 1024;
    public static final int bufferOverlap = bufferSize / 2;

    private final ParametersProvider parametersProvider;
    private final int sampleRate;
    private final float[] outputAccumulator;
    private final long osamp;

    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final DispatchQueue finalizingQueue = new DispatchQueue("FormantShifterFinalizing");
    private final BlockingQueue<AudioEvent> audioEventQueue = new LinkedBlockingQueue<>();

    private boolean needFinishProcessing = false;
    private boolean forceFinishProcessing = false;

    public FormantShifter(ParametersProvider parametersProvider, int sampleRate) {
        this.parametersProvider = parametersProvider;
        this.sampleRate = sampleRate;

        outputAccumulator = new float[bufferSize * 2];
        osamp = bufferSize / (bufferSize - bufferOverlap);
    }

    @Override
    public void processingFinished() {
        synchronized (this) {
            if (!needFinishProcessing) {
                needFinishProcessing = true;
            } else {
                forceFinishProcessing = true;
            }
        }
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        AudioEvent audioEventCopy = cloneAudioEvent(audioEvent);
        audioEventQueue.add(audioEventCopy);
        threadPoolExecutor.execute(() -> {
            float[] shiftedAudioBuffer = shiftFormants(audioEventCopy.getFloatBuffer());
            finalizingQueue.postRunnable(() -> shiftingFinished(audioEventCopy, shiftedAudioBuffer));
        });
        return true;
    }

    private AudioEvent cloneAudioEvent(AudioEvent audioEvent) {
        TarsosDSPAudioFormat dspFormat = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        AudioEvent audioEventCopy = new AudioEvent(dspFormat);
        audioEventCopy.setOverlap(audioEvent.getOverlap());
        audioEventCopy.setFloatBuffer(audioEvent.getFloatBuffer().clone());
        audioEventCopy.setBytesProcessed(audioEvent.getSamplesProcessed() * dspFormat.getFrameSize());
        return audioEventCopy;
    }

    private float[] shiftFormants(float[] srcFloatBuffer) {
        float[] tempAudioBuffer = new float[srcFloatBuffer.length * 4];

        int tempAudioBufferLength = WorldUtils.shiftFormants(
                parametersProvider.getF0Shift(),
                parametersProvider.getFormantRatio(),
                sampleRate,
                srcFloatBuffer,
                srcFloatBuffer.length,
                tempAudioBuffer,
                VoiceChangeSettings.formantShiftingHarvest.get().orElse(false) ? 1 : 0
        );

        Resampler r = new Resampler(false,0.1,4.0);
        double factor = (double)srcFloatBuffer.length / tempAudioBufferLength;
        float[] audioBufferResized = new float[srcFloatBuffer.length];
        r.process(factor, tempAudioBuffer, 0, tempAudioBufferLength, true, audioBufferResized, 0, audioBufferResized.length);
        return audioBufferResized;
    }

    private float clipAudioSample(float value) {
        return Math.max(Math.min(value, 1.0f), -1.0f);
    }

    private void shiftingFinished(AudioEvent audioEvent, float[] shiftedAudioBuffer) {
        boolean currentEventIsFirstInQueue = checkHeadAudioEventInQueueAndRemoveIfNeeded(audioEvent);
        if (!currentEventIsFirstInQueue) {
            finalizingQueue.postRunnable(() -> shiftingFinished(audioEvent, shiftedAudioBuffer));
            return;
        }

        overlapAdd(outputAccumulator, shiftedAudioBuffer);
        updateAudioEventBuffer(audioEvent);
        if (nextAudioProcessor != null) {
            nextAudioProcessor.process(audioEvent);
        }
        synchronized (this) {
            if (needFinishProcessing && audioEventQueue.isEmpty() || forceFinishProcessing) {
                actualFinishProcessing();
            }
        }
    }

    private boolean checkHeadAudioEventInQueueAndRemoveIfNeeded(AudioEvent targetAudioEvent) {
        if (audioEventQueue.peek() == targetAudioEvent) {
            try {
                audioEventQueue.take();
                return true;
            } catch (InterruptedException ignore) {
            }
        }
        return false;
    }

    private void overlapAdd(float[] outputAccumulator, float[] audioBuffer) {
        for(int i = 0; i < audioBuffer.length ; i ++){
            float window = (float) (-0.5 * Math.cos(2.0 * Math.PI * (double)i / (double)bufferSize) + 0.5);
            float currentValue = window * audioBuffer[i]/(float) osamp;
            if (osamp == 1) {
                outputAccumulator[i] = currentValue;
            } else {
                outputAccumulator[i] = outputAccumulator[i] + currentValue;
            }
            outputAccumulator[i] = clipAudioSample(outputAccumulator[i]);
        }
        int stepSize = (int) (bufferSize/osamp);
        if (osamp != 1) {
            System.arraycopy(outputAccumulator, stepSize, outputAccumulator, 0, bufferSize);
        }
    }

    private void updateAudioEventBuffer(AudioEvent audioEvent) {
        int stepSize = (int) (bufferSize/osamp);
        float[] audioBuffer = new float[audioEvent.getFloatBuffer().length];
        audioEvent.setFloatBuffer(audioBuffer);
        System.arraycopy(outputAccumulator, 0, audioBuffer, bufferSize-stepSize, stepSize);
    }

    private void actualFinishProcessing() {
        threadPoolExecutor.shutdown();
        finalizingQueue.recycle();
        if (nextAudioProcessor != null) {
            nextAudioProcessor.processingFinished();
        }
    }
}
