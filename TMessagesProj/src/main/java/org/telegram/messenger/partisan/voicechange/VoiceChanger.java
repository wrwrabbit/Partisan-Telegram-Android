package org.telegram.messenger.partisan.voicechange;

import com.google.common.base.Strings;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.ui.TesterSettingsActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.PitchShifter;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;

public class VoiceChanger {
    private final int sampleRate;
    private final VoiceChangePipedInputStream pipedInputStream;
    private final VoiceChangePipedOutputStream pipedOutputStream;
    private final AudioDispatcher dispatcher;
    private final Thread thread;
    private final AudioSaverProcessor audioSaver;
    private final DispatchQueue writeQueue = new DispatchQueue("voiceChangerWriteQueue");

    private ChainedAudioProcessor lastAudioProcessorInChain;

    public VoiceChanger(int sampleRate) {
        this.sampleRate = sampleRate;
        pipedOutputStream = new VoiceChangePipedOutputStream();
        try {
            pipedInputStream = new VoiceChangePipedInputStream(pipedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dispatcher = createAudioDispatcher(pipedInputStream);
        audioSaver = new AudioSaverProcessor();
        Map<Integer, Integer> spectrumDistortionMap = createSpectrumDistortionMap();
        if (formantShiftingEnabled()) {
            addAudioProcessorToChain(new FormantShifter(TesterSettingsActivity.f0Shift, TesterSettingsActivity.formantRatio, sampleRate));
        } else if (spectrumDistortionMap != null) {
            addAudioProcessorToChain(new SpectrumDistorter(spectrumDistortionMap, sampleRate, Constants.bufferSize, Constants.bufferOverlap));
        } else if (pitchShiftingEnabled()) {
            addAudioProcessorToChain(new ChainedAudioProcessor() {
                private final PitchShifter shifter = new PitchShifter(TesterSettingsActivity.pitchFactor, sampleRate, Constants.bufferSize, Constants.bufferOverlap);
                @Override
                public void processingFinished() {
                    shifter.processingFinished();
                }

                @Override
                public boolean processInternal(AudioEvent audioEvent) {
                    return shifter.process(audioEvent);
                }
            });
        }
        List<TimeDistorter.DistortionInterval> timeDistortionList = createTimeDistortionList();
        if (timeDistortionList != null) {
            addAudioProcessorToChain(new TimeDistorter(timeDistortionList));
        } else if (timeStretchEnabled()) {
            addAudioProcessorToChain(new TimeStretcher(TesterSettingsActivity.timeStretchFactor));
        }
        addAudioProcessorToChain(audioSaver);

        thread = new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
    }

    private void addAudioProcessorToChain(ChainedAudioProcessor processor) {
        if (lastAudioProcessorInChain != null) {
            lastAudioProcessorInChain.setNextAudioProcessor(processor);
        } else {
            dispatcher.addAudioProcessor(processor);
        }
        lastAudioProcessorInChain = processor;
    }

    private Map<Integer, Integer> createSpectrumDistortionMap() {
        Map<Integer, Integer> distortionMap = accumulateDistortionParams(
                TesterSettingsActivity.spectrumDistorterParams,
                new HashMap<>(),
                (map, distortionParts) -> {
            int fromHz = Integer.parseInt(distortionParts[0]);
            int toHz = Integer.parseInt(distortionParts[1]);
            int fromIndex = (fromHz * Constants.bufferSize) / sampleRate;
            int toIndex = (toHz * Constants.bufferSize) / sampleRate;
            map.put(fromIndex, toIndex);
        });
        return distortionMap != null && !distortionMap.isEmpty() ? distortionMap : null;
    }

    private List<TimeDistorter.DistortionInterval> createTimeDistortionList() {
        List<TimeDistorter.DistortionInterval> distortionMap = accumulateDistortionParams(
                TesterSettingsActivity.timeDistortionParams,
                new ArrayList<>(),
                (list, distortionParts) -> {
            TimeDistorter.DistortionInterval interval = new TimeDistorter.DistortionInterval();
            interval.length = Double.parseDouble(distortionParts[0]);
            interval.stretchFactor = Float.parseFloat(distortionParts[1]);
            list.add(interval);
        });
        return distortionMap != null && !distortionMap.isEmpty() ? distortionMap : null;
    }

    private <T> T accumulateDistortionParams(String params, T collection, BiConsumer<T, String[]> accumulationFunction) {
        if (Strings.isNullOrEmpty(params)) {
            return null;
        }
        try {
            String[] distortionStrings = params.split(",");
            for (String distortionString : distortionStrings) {
                if (Strings.isNullOrEmpty(distortionString)) {
                    return null;
                }
                String[] distortionParts = distortionString.split(":");
                if (distortionParts.length != 2) {
                    return null;
                }
                accumulationFunction.accept(collection, distortionParts);
            }
            return collection;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] changeVoice(byte[] data) {
        if (thread.getState() == Thread.State.NEW) {
            thread.start();
        }
        writeQueue.postRunnable(() -> {
            try {
                pipedOutputStream.write(data);
            } catch (Exception e) {
                PartisanLog.e(e);
            }
        });

        return audioSaver.getAndResetByteArray();
    }

    public void stop() {
        try {
            dispatcher.stop();
            pipedOutputStream.close();
            pipedInputStream.close();
            writeQueue.recycle();
            thread.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AudioDispatcher createAudioDispatcher(InputStream inputStream) {
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        return new AudioDispatcher(
                new UniversalAudioInputStream(inputStream, format),
                Constants.bufferSize,
                Constants.bufferOverlap
        );
    }

    public static boolean needChangeVoice() {
        return pitchShiftingEnabled()
                || timeStretchEnabled()
                || spectrumDistortionEnabled()
                || timeDistortionEnabled()
                || formantShiftingEnabled();
    }

    private static boolean pitchShiftingEnabled() {
        return Math.abs(TesterSettingsActivity.pitchFactor - 1.0) > 0.01;
    }

    private static boolean timeStretchEnabled() {
        return Math.abs(TesterSettingsActivity.timeStretchFactor - 1.0) > 0.01;
    }

    private static boolean spectrumDistortionEnabled() {
        return !Strings.isNullOrEmpty(TesterSettingsActivity.spectrumDistorterParams);
    }

    private static boolean timeDistortionEnabled() {
        return !Strings.isNullOrEmpty(TesterSettingsActivity.timeDistortionParams);
    }

    private static boolean formantShiftingEnabled() {
        return Math.abs(TesterSettingsActivity.f0Shift - 1.0) > 0.01
                || Math.abs(TesterSettingsActivity.formantRatio - 1.0) > 0.01;
    }
}
