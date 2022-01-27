package com.github.aecsocket.demeter.core;

import com.github.aecsocket.demeter.core.factor.Factor;

public record ClimateState(
    double temperature,
    double humidity,
    double cloudCoverage
) implements Factor<ClimateState> {
    @Override
    public ClimateState add(ClimateState other) {
        return new ClimateState(
            temperature + other.temperature,
            humidity + other.humidity,
            cloudCoverage + other.cloudCoverage
        );
    }
}
