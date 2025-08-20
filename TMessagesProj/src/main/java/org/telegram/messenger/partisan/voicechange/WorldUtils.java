package org.telegram.messenger.partisan.voicechange;

public class WorldUtils {
    public static native int shiftFormants(double shift, double ratio, int fs, float[] x, int x_length, float[] y);
}
