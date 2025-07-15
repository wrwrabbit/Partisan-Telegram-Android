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

    public VoiceChanger(int sampleRate) {
        this.sampleRate = sampleRate;
        pipedOutputStream = new VoiceChangePipedOutputStream();
        try {
            pipedInputStream = new VoiceChangePipedInputStream(pipedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        dispatcher = createAudioDispatcher(pipedInputStream);
        BufferRestorer bufferRestorer = new BufferRestorer(dispatcher);
        audioSaver = new AudioSaverProcessor();
        dispatcher.addAudioProcessor(bufferRestorer.createPreProcessor());
        Map<Integer, Integer> spectrumDistortionMap = createSpectrumDistortionMap();
        if (spectrumDistortionMap != null) {
            dispatcher.addAudioProcessor(new SpectrumDistorter(spectrumDistortionMap, sampleRate, Constants.bufferSize, Constants.bufferOverlap));
        } else {
            dispatcher.addAudioProcessor(new PitchShifter(TesterSettingsActivity.pitchFactor, sampleRate, Constants.bufferSize, Constants.bufferOverlap));
        }
        List<TimeDistorter.DistortionInterval> timeDistortionList = createTimeDistortionList();
        if (timeDistortionList != null) {
            dispatcher.addAudioProcessor(new TimeDistorter(dispatcher, timeDistortionList));
        } else {
            dispatcher.addAudioProcessor(new TimeStretcher(dispatcher, TesterSettingsActivity.timeStretchFactor));
        }
        dispatcher.addAudioProcessor(audioSaver);
        dispatcher.addAudioProcessor(bufferRestorer.createPostProcessor());

        thread = new Thread(() -> {
            try {
                dispatcher.run();
            } catch (Throwable ignore) {
            }
        });
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
        return Math.abs(TesterSettingsActivity.pitchFactor - 1.0) > 0.01
                || Math.abs(TesterSettingsActivity.timeStretchFactor - 1.0) > 0.01
                || !Strings.isNullOrEmpty(TesterSettingsActivity.spectrumDistorterParams)
                || !Strings.isNullOrEmpty(TesterSettingsActivity.timeDistortionParams);
    }
}
