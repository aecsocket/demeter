package com.github.aecsocket.demeter.core;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import com.github.aecsocket.demeter.core.config.ClimateConfig;
import com.github.aecsocket.minecommons.core.vector.cartesian.Vector2;

public class EngineTest {
    int barWidth = 70;
    double barScale = 1;
    String barEmpty = " ".repeat(barWidth);

    String bar(double value) {
        int chars = (int) ((Math.abs(value) * barWidth) / barScale);

        String bar;
        if (value >= 0) {
            bar = "." + barEmpty + "|" + "+".repeat(Math.min(barWidth, chars)) + (chars > barWidth ? ">" : " ".repeat(barWidth - chars) + ".");
        } else {
            bar = (chars > barWidth ? "<" : "." + " ".repeat(barWidth - chars)) + "-".repeat(Math.min(barWidth, chars)) + "|" + barEmpty + ".";
        }
        return bar;
    }

    double map(double raw) {
        return raw * 20 + 15;
    }

    void logValue(double day, double maxDay, String format, double value) {
        String dayText = String.format(format, day), maxDayText = String.format(format, maxDay);
        String label = String.format("%" + maxDayText.length() + "s", dayText);
        System.out.println(label + " " + bar(value) + " " +
                (value >= 0 ? " " : "-") + String.format("%.3f", Math.abs(value)) + " => " + String.format("%.1f", map(value)) + "c");
    }

    @Test
    void test() {
        ClimateEngine engine = new ClimateEngine(
            new ClimateConfig(
                null, true,
                new ClimateConfig.Noise(
                    0.1, 1000, 1, 0, 0
                ),
                new ClimateConfig.Noise(
                    0.1, 1000, 5, 5, 0.75
                ),
                new ClimateConfig.Factors(
                    0.2
                ),
                Collections.emptyMap()
            ),
            100
        );

        for (double d = 0; d < 1; d += 0.01) {
            logValue(d, 1, "%.2f", engine.state(d, Vector2.ZERO).get().humidity());
        }

        for (double d = 0; d < 100; d += 1) {
            logValue(d, 100, "%.1f", engine.state(d, Vector2.ZERO).get().humidity());
            logValue(d + 0.5, 100, "%.1f", engine.state(d + 0.5, Vector2.ZERO).get().humidity());
        }
    }
}
