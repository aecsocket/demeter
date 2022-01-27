package com.github.aecsocket.demeter.core;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.github.aecsocket.demeter.core.config.ClimateConfig;
import com.github.aecsocket.minecommons.core.vector.cartesian.Vector2;

import static java.lang.Math.*;

public final class ClimateEngine {
    private final ClimateConfig config;
    private final PerlinNoise noise;

    public ClimateEngine(ClimateConfig config, PerlinNoise noise) {
        this.config = config;
        this.noise = noise;
    }

    public ClimateEngine(ClimateConfig config, long worldSeed) {
        this.config = config;
        noise = new PerlinNoise(new Random(config.seed() == null
                ? config.useWorldSeed() ? worldSeed : ThreadLocalRandom.current().nextLong()
                : config.seed()
        ));
    }

    public ClimateConfig config() { return config; }

    public double noise(double day, Vector2 pos) {
        double scale = config.noise().scale();
        if (scale > 0) {
            pos = pos.divide(scale);
        } else {
            pos = Vector2.ZERO;
        }
        return noise.get(day * config.noise().timeFactor(), pos.x(), pos.y(), config.noise().octaves(), config.noise().frequency(), config.noise().amplitude());
    }

    public ClimateState state(double day, Vector2 pos) {
        double temperature = noise(day, pos);
        temperature += sin((day * 2 * PI) - (0.5 * PI)) * config.factors().dayTimeAmplitude();
        
        return new ClimateState(
            temperature,
        0, 0);
    }
}
