package com.gitlab.aecsocket.demeter.paper.util;

public class GrassColors extends ImageColors {
    public static final int DEFAULT_COLOR = 0x00ff01;

    public GrassColors(int width, int height, int[] pixels) {
        super(width, height, pixels);
    }

    public GrassColors(ImageColors o) {
        super(o);
    }

    @Override
    public int get(double temp, double humid) {
        int idx = index(temp, humid);
        return idx > pixels.length ? DEFAULT_COLOR : pixels[idx];
    }
}
