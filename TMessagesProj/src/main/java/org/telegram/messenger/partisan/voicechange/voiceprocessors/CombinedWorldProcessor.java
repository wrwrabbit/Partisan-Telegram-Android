package org.telegram.messenger.partisan.voicechange.voiceprocessors;

import org.telegram.messenger.partisan.voicechange.FormantShiftLimits;
import org.telegram.messenger.partisan.voicechange.ParametersProvider;
import org.telegram.messenger.partisan.voicechange.WorldVocoder;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

public class CombinedWorldProcessor extends ChainedAudioProcessor {
    private static class ShiftParameter {
        public double from;
        public double to;

        public ShiftParameter(double from, double to) {
            this.from = from;
            this.to = to;
        }
    }

    private static final double MAX_SPREAD_SPEED = 0.2;
    private static final int BUFFER_LENGTH_MS = 350;
    public final int bufferSize;
    public final int bufferOverlap;
    private final ShiftParameter currentF0Shift;
    private final ShiftParameter currentLowRatio;
    private final ShiftParameter currentMidRatio;
    private final ShiftParameter currentHighRatio;

    private final ParametersProvider parametersProvider;
    private final int sampleRate;
    private final float[] outputAccumulator;
    private final long osamp;

    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final ThreadPoolExecutor finalizingQueue = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final BlockingQueue<AudioEvent> audioEventQueue = new LinkedBlockingQueue<>();

    private boolean needFinishProcessing = false;
    private boolean forceFinishProcessing = false;

    public CombinedWorldProcessor(ParametersProvider parametersProvider, int sampleRate) {
        this.parametersProvider = parametersProvider;
        this.sampleRate = sampleRate;

        bufferSize = (int)(BUFFER_LENGTH_MS / 1000.0 * sampleRate) + 1;
        bufferOverlap = 0;

        outputAccumulator = new float[bufferSize * 2];
        osamp = bufferSize / (bufferSize - bufferOverlap);

        currentF0Shift = generateFirstShift(parametersProvider.getF0Shift());
        currentLowRatio = generateFirstShift(parametersProvider.getLowRatio());
        currentMidRatio = generateFirstShift(parametersProvider.getMidRatio());
        currentHighRatio = generateFirstShift(parametersProvider.getHighRatio());
    }

    private ShiftParameter generateFirstShift(double defaultValue) {
        FormantShiftLimits limits = getSpreadLimits(defaultValue);
        if (limits == null) {
            return new ShiftParameter(defaultValue, defaultValue);
        }
        double value = ThreadLocalRandom.current().nextDouble(limits.min, limits.max);
        return new ShiftParameter(value, value);
    }

    private FormantShiftLimits getSpreadLimits(double defaultValue) {
        double maxFormantSpread = parametersProvider.getMaxFormantSpread();
        if (maxFormantSpread < 1E-6) {
            return null;
        }
        FormantShiftLimits spreadLimits = new FormantShiftLimits();
        spreadLimits.min = defaultValue / (1.0 + maxFormantSpread);
        spreadLimits.max = defaultValue * (1.0 + maxFormantSpread);
        FormantShiftLimits maxLimits = parametersProvider.getFormantShiftLimits(defaultValue);
        spreadLimits.min = Math.max(spreadLimits.min, maxLimits.min);
        spreadLimits.max = Math.min(spreadLimits.max, maxLimits.max);
        return spreadLimits;
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
        ShiftParameter f0Shift = generateNewShift(parametersProvider.getF0Shift(), currentF0Shift);
        ShiftParameter lowRatio = generateNewShift(parametersProvider.getLowRatio(), currentLowRatio);
        ShiftParameter midRatio = generateNewShift(parametersProvider.getMidRatio(), currentMidRatio);
        ShiftParameter highRatio = generateNewShift(parametersProvider.getHighRatio(), currentHighRatio);
        threadPoolExecutor.execute(() -> {
            float[] shiftedAudioBuffer = changeVoice(audioEventCopy.getFloatBuffer(), f0Shift, lowRatio, midRatio, highRatio);
            finalizingQueue.execute(() -> shiftingFinished(audioEventCopy, shiftedAudioBuffer));
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

    private ShiftParameter generateNewShift(double defaultValue, ShiftParameter shiftParameter) {
        FormantShiftLimits limits = getSpreadLimits(defaultValue);
        if (limits == null) {
            return shiftParameter;
        }
        double maxChange = (limits.max - limits.min) * MAX_SPREAD_SPEED;
        double newValue;
        do {
            double change = ThreadLocalRandom.current().nextDouble(-maxChange, maxChange);
            newValue = shiftParameter.to + change;
        } while(newValue < limits.min || newValue > limits.max);
        shiftParameter.from = shiftParameter.to;
        return new ShiftParameter(shiftParameter.from, shiftParameter.to);
    }

    private float[] changeVoice(float[] srcFloatBuffer,
                                ShiftParameter f0Shift,
                                ShiftParameter lowRatio,
                                ShiftParameter midRatio,
                                ShiftParameter highRatio) {
        float[] audioBufferResult = new float[srcFloatBuffer.length];
        WorldVocoder.changeVoice(
                f0Shift.from,
                f0Shift.to,
                lowRatio.from,
                lowRatio.to,
                midRatio.from,
                midRatio.to,
                highRatio.from,
                highRatio.to,
                sampleRate,
                srcFloatBuffer,
                srcFloatBuffer.length,
                audioBufferResult,
                parametersProvider.shiftFormantsWithHarvest() ? 1 : 0,
                parametersProvider.getBadSThreshold(),
                parametersProvider.getBadSCutoff(),
                parametersProvider.getBadShMinThreshold(),
                parametersProvider.getBadShMaxThreshold(),
                parametersProvider.getBadShCutoff()
        );
        return audioBufferResult;
    }

    private float clipAudioSample(float value) {
        return Math.max(Math.min(value, 1.0f), -1.0f);
    }

    private void shiftingFinished(AudioEvent audioEvent, float[] shiftedAudioBuffer) {
        boolean currentEventIsFirstInQueue = checkHeadAudioEventInQueueAndRemoveIfNeeded(audioEvent);
        if (!currentEventIsFirstInQueue) {
            finalizingQueue.execute(() -> shiftingFinished(audioEvent, shiftedAudioBuffer));
            return;
        }

        mergeAudioBufferWithOutput(outputAccumulator, shiftedAudioBuffer);
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

    private void mergeAudioBufferWithOutput(float[] outputAccumulator, float[] audioBuffer) {
        if (osamp == 1) {
            Arrays.fill(outputAccumulator, 0f);
        }
        for (int i = 0; i < audioBuffer.length; i++) {
            if (osamp == 1) {
                outputAccumulator[i] = audioBuffer[i];
            } else {
                outputAccumulator[i] = outputAccumulator[i] + audioBuffer[i];
                if (i < bufferOverlap || i > bufferSize - bufferOverlap) {
                    outputAccumulator[i] /= 2;
                }
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
        finalizingQueue.shutdown();
        if (nextAudioProcessor != null) {
            nextAudioProcessor.processingFinished();
        }
    }
}
