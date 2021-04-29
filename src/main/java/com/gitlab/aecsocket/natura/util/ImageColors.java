package com.gitlab.aecsocket.natura.util;

import com.gitlab.aecsocket.unifiedframework.core.util.Utils;

import java.awt.image.BufferedImage;

public class ImageColors {
    private final int width;
    private final int height;
    private final int[] pixels;

    public ImageColors(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public ImageColors(ImageColors o) {
        width = o.width;
        height = o.height;
        pixels = o.pixels.clone();
    }

    public int width() { return width; }
    public int height() { return height; }
    public int[] pixels() { return pixels; }

    protected int getIndex(double temperature, double rainfall) {
        temperature = Utils.clamp01(temperature);
        rainfall = Utils.clamp01(rainfall);

        rainfall *= temperature;
        int x = (int) ((1 - temperature) * (width - 1));
        int y = (int) ((1 - rainfall) * (height - 1));
        return (y * width) + x;
    }

    public int get(double temperature, double rainfall) {
        return pixels[getIndex(temperature, rainfall)];
    }

    public static ImageColors load(BufferedImage image) {
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
        return new ImageColors(width, height, pixels);
    }
}
