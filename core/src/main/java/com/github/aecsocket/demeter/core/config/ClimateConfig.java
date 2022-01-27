package com.github.aecsocket.demeter.core.config;

import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.meta.Required;

public record ClimateConfig(
    @Nullable Long seed,
    boolean useWorldSeed,
    Noise temperatureNoise,
    Noise humidityNoise,
    Factors factors,
    @Required Map<String, Season> seasons
) {
    public record Noise(
        double timeFactor,
        double scale,
        int octaves,
        double lacunarity,
        double persistence
    ) {}

    public record Factors(
        double dayTimeAmplitude
    ) {}
}
