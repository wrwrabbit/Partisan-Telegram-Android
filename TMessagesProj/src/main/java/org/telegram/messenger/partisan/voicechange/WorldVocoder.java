package org.telegram.messenger.partisan.voicechange;

public class WorldVocoder {
    public static native int changeVoice(double shift_from, double shift_to,
                                         double low_ratio_from, double low_ratio_to,
                                         double mid_ratio_from, double mid_ratio_to,
                                         double high_ratio_from, double high_ratio_to,
                                         int fs, float[] x, int x_length, float[] y, int harvest,
                                         int bad_s_threshold, int bad_s_cutoff,
                                         int bad_sh_min_threshold, int bad_sh_max_threshold, int bad_sh_cutoff);
}
