package com.gitlab.aecsocket.natura.util;

import com.gitlab.aecsocket.unifiedframework.core.util.Utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class FoliageColors {
    private final int width;
    private final int height;
    private final int[] pixels;

    public FoliageColors(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int[] pixels() { return pixels; }

    public int get(double temperature, double rainfall) {
        temperature = Utils.clamp01(temperature);
        rainfall = Utils.clamp01(rainfall);

        rainfall *= temperature;
        int x = (int) ((1 - temperature) * (width - 1));
        int y = (int) ((1 - rainfall) * (height - 1));
        return pixels[(y * width) + x];
    }

    public static FoliageColors load(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[i] = image.getRGB(x, y);
                ++i;
            }
        }
        return new FoliageColors(width, height, pixels);
    }
}
