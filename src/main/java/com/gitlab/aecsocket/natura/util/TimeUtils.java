package com.gitlab.aecsocket.natura.util;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.data.Tuple2;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtils {
    private TimeUtils() {}

    private static final Pattern percent = Pattern.compile("([0-9.]+)%");
    private static final Pattern days = Pattern.compile("([0-9.]+)(days|d)");
    private static final Pattern hours = Pattern.compile("([0-9.]+)(hours|h)");
    private static final Pattern minutes = Pattern.compile("([0-9.]+)(minutes|m)");
    private static final Pattern seconds = Pattern.compile("([0-9.]+)(seconds|s)");

    private static double tryParse(String number) throws SerializationException {
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw new SerializationException(e);
        }
    }

    public static double timeMultiplier(String input) throws SerializationException {
        Matcher match = percent.matcher(input);
        if (match.find())
            return tryParse(match.group(1)) / 100;
        Tuple2<Double, Boolean> result = time(input);
        return result.b() ? result.a() / NaturaPlugin.TICKS_PER_DAY : result.a();
    }

    public static Tuple2<Double, Boolean> time(String input) throws SerializationException {
        Matcher match = days.matcher(input);
        if (match.find())
            return Tuple2.of((tryParse(match.group(1)) * 60 * 60 * 24 * Utils.TPS), true);

        match = hours.matcher(input);
        if (match.find())
            return Tuple2.of((tryParse(match.group(1)) * 60 * 60 * Utils.TPS), true);

        match = minutes.matcher(input);
        if (match.find())
            return Tuple2.of((tryParse(match.group(1)) * 60 * Utils.TPS), true);

        match = seconds.matcher(input);
        if (match.find())
            return Tuple2.of((tryParse(match.group(1)) * Utils.TPS), true);

        return Tuple2.of(tryParse(input), false);
    }
}
