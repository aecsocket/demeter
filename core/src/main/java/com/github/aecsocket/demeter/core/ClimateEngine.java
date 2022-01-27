package com.github.aecsocket.demeter.core;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.github.aecsocket.demeter.core.config.ClimateConfig;
import com.github.aecsocket.demeter.core.factor.Factors;
import com.github.aecsocket.minecommons.core.vector.cartesian.Vector2;

import static java.lang.Math.*;

public final class ClimateEngine {
    public static final String FACTOR_NOISE = "noise";
    public static final String FACTOR_DAY_TIME = "day_time";

    private final ClimateConfig config;
    private final PerlinNoise noise;
    private final int[] offsets = new int[2];

    public ClimateEngine(ClimateConfig config, long worldSeed) {
        this.config = config;
        Random random = new Random(
            config.seed() != null
                ? config.seed()
                : config.useWorldSeed() ? worldSeed : ThreadLocalRandom.current().nextLong()
        );
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = random.nextInt(1024);
        }
        noise = new PerlinNoise(random);
    }

    public ClimateConfig config() { return config; }

    private double noise(double day, Vector2 pos, ClimateConfig.Noise options, int offsetKey) {
        double scale = options.scale();
        if (scale > 0) {
            pos = pos.divide(scale);
        } else {
            pos = Vector2.ZERO;
        }
        double offset = offsets[offsetKey];
        return noise.get(day * options.timeFactor() + offset, pos.x() + offset, pos.y() + offset, options.octaves(), options.lacunarity(), options.persistence());
    }

    private double temperatureNoise(double day, Vector2 pos) {
        return noise(day, pos, config.temperatureNoise(), 0);
    }

    private double humidityNoise(double day, Vector2 pos) {
        return noise(day, pos, config.humidityNoise(), 1);
    }

    public Factors<ClimateState> state(double day, Vector2 pos) {
        var factors = Factors.<ClimateState>builder();
        factors.add(FACTOR_NOISE, new ClimateState(
            temperatureNoise(day, pos), humidityNoise(day, pos), 0
        ));
        factors.add(FACTOR_DAY_TIME, new ClimateState(
            sin((day * 2 * PI) - (0.5 * PI)) * config.factors().dayTimeAmplitude(), 0, 0
        ));
        return factors.build();
    }
}
