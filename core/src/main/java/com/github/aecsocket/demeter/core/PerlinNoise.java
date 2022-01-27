package com.github.aecsocket.demeter.core;

import java.util.Random;

// Adapted from Bukkit's Perlin noise gen
public final class PerlinNoise {
    private final int[] perm = new int[512];
    private final double offsetX, offsetY, offsetZ;

    public PerlinNoise(Random random) {
        offsetX = random.nextDouble() * 256;
        offsetY = random.nextDouble() * 256;
        offsetZ = random.nextDouble() * 256;

        for (int i = 0; i < 256; i++) {
            perm[i] = random.nextInt(256);
        }

        for (int i = 0; i < 256; i++) {
            int pos = random.nextInt(256 - i) + i;
            int old = perm[i];

            perm[i] = perm[pos];
            perm[pos] = old;
            perm[i + 256] = perm[i];
        }
    }

    public double get(double x, double y, double z) {
        x += offsetX;
        y += offsetY;
        z += offsetZ;

        int floorX = floor(x);
        int floorY = floor(y);
        int floorZ = floor(z);

        // Find unit cube containing the point
        int X = floorX & 255;
        int Y = floorY & 255;
        int Z = floorZ & 255;

        // Get relative xyz coordinates of the point within the cube
        x -= floorX;
        y -= floorY;
        z -= floorZ;

        // Compute fade curves for xyz
        double fX = fade(x);
        double fY = fade(y);
        double fZ = fade(z);

        // Hash coordinates of the cube corners
        int A = perm[X] + Y;
        int AA = perm[A] + Z;
        int AB = perm[A + 1] + Z;
        int B = perm[X + 1] + Y;
        int BA = perm[B] + Z;
        int BB = perm[B + 1] + Z;

        return lerp(fZ, lerp(fY, lerp(fX, grad(perm[AA], x, y, z),
                        grad(perm[BA], x - 1, y, z)),
                    lerp(fX, grad(perm[AB], x, y - 1, z),
                        grad(perm[BB], x - 1, y - 1, z))),
                lerp(fY, lerp(fX, grad(perm[AA + 1], x, y, z - 1),
                        grad(perm[BA + 1], x - 1, y, z - 1)),
                    lerp(fX, grad(perm[AB + 1], x, y - 1, z - 1),
                        grad(perm[BB + 1], x - 1, y - 1, z - 1))));
    }

    // [-1, 1]
    public double get(double x, double y, double z, int octaves, double frequency, double amplitude) {
        double result = 0;
        double amp = 1;
        double freq = 1;
        double max = 0;

        for (int i = 0; i < octaves; i++) {
            result += get(x * freq, y * freq, z * freq) * amp;
            max += amp;
            freq *= frequency;
            amp *= amplitude;
        }

        return result / max;
    }

    private static int floor(double x) {
        return x >= 0 ? (int) x : (int) x - 1;
    }

    private static double fade(double x) {
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    private static double lerp(double x, double y, double z) {
        return y + x * (z - y);
    }

    private static double grad(int hash, double x, double y, double z) {
        hash &= 15;
        double u = hash < 8 ? x : y;
        double v = hash < 4 ? y : hash == 12 || hash == 14 ? x : z;
        return ((hash & 1) == 0 ? u : -u) + ((hash & 2) == 0 ? v : -v);
    }
}
