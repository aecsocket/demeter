package com.github.aecsocket.demeter.core;

public record ClimateState(
    double temperature,
    double humidity,
    double cloudCoverage
) {}
