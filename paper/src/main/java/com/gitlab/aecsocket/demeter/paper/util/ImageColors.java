package com.gitlab.aecsocket.demeter.paper.util;

import com.gitlab.aecsocket.minecommons.core.Numbers;

import java.awt.image.BufferedImage;

public class ImageColors {
    protected final int width;
    protected final int height;
    protected final int[] pixels;

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

    protected int index(double temp, double humid) {
        temp = Numbers.clamp01(temp);
        humid = Numbers.clamp01(humid);

        humid *= temp;
        int x = (int) ((1 - temp) * (width - 1));
        int y = (int) ((1 - humid) * (height - 1));
        return (y * width) + x;
    }

    public int get(double temp, double humid) {
        return pixels[index(temp, humid)];
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
