package com.gitlab.aecsocket.natura.util;

public class GrassColors extends ImageColors {
    public static final int DEFAULT_COLOR = 0x00ff01;

    public GrassColors(int width, int height, int[] pixels) {
        super(width, height, pixels);
    }

    public GrassColors(ImageColors o) {
        super(o);
    }

    @Override
    public int get(double temperature, double rainfall) {
        int idx = getIndex(temperature, rainfall);
        return idx > pixels().length ? DEFAULT_COLOR : pixels()[idx];
    }
}
