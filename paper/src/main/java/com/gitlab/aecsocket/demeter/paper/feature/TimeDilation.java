package com.gitlab.aecsocket.demeter.paper.feature;

import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.core.Ticks;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.serializers.Serializers;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public class TimeDilation extends Feature<TimeDilation.Config> {
    public static final String ID = "time_dilation";
    public static final int TICKS_PER_STAGE = Ticks.TPS * 60 * 10;
    public static final int TICKS_PER_DAY = TICKS_PER_STAGE * 2;
    public static final long MS_PER_STAGE = 1000 * 60 * 10;

    @ConfigSerializable
    public record Config(
            @Required WorldsConfig<WorldConfig> worlds
    ) {}

    public record Factor(double value) {
        public Duration asDuration() {
            return Duration.duration((long) (MS_PER_STAGE / value));
        }

        public static Factor fromDuration(Duration duration) {
            return new Factor(TICKS_PER_STAGE / (double) duration.ticks());
        }

        public static final class Serializer implements TypeSerializer<Factor> {
            @Override
            public void serialize(Type type, @Nullable Factor obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    if (Double.compare(obj.value, 1) == 0)
                        node.set(obj.value);
                    else
                        node.set(obj.asDuration());
                }
            }

            @Override
            public Factor deserialize(Type type, ConfigurationNode node) throws SerializationException {
                if (node.raw() instanceof String string && string.length() >= 2) {
                    char last = string.charAt(string.length() - 1);
                    switch (last) {
                        case 'x', '%' -> {
                            try {
                                double factor = Double.parseDouble(string.substring(0, string.length() - 1));
                                return new Factor(last == '%' ? factor / 100 : factor);
                            } catch (NumberFormatException e) {
                                throw new SerializationException(node, type, "Could not parse factor", e);
                            }
                        }
                    }
                }
                return fromDuration(Serializers.require(node, Duration.class));
            }
        }
    }

    @ConfigSerializable
    public record WorldConfig(
            @Required Factor dayDuration,
            @Required Factor nightDuration
    ) {}

    public enum DayStage {
        DAY     ("day"),
        NIGHT   ("night"),
        NONE    ("none");

        private final String key;

        DayStage(String key) {
            this.key = key;
        }

        public String key() { return key; }
    }

    private final Object2DoubleMap<World> elapsed = new Object2DoubleArrayMap<>();

    public TimeDilation(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    public boolean isNight(long ticks) {
        return ticks % TICKS_PER_DAY > TICKS_PER_STAGE;
    }

    public DayStage dayStage(World world) {
        return world.getEnvironment() == World.Environment.NORMAL
                ? isNight(world.getFullTime()) ? DayStage.NIGHT : DayStage.DAY
                : DayStage.NONE;
    }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
    }

    @Override
    public void enable() {
        if (config == null)
            return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (World world : Bukkit.getWorlds()) {
                //noinspection ConstantConditions
                if (!world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))
                    continue;
                config.worlds.get(world).ifPresent(worldConfig -> {
                    double elapsed = this.elapsed.getOrDefault(world, 0d) +
                             (dayStage(world) == DayStage.NIGHT ? worldConfig.nightDuration : worldConfig.dayDuration).value;
                    long time = world.getFullTime() - 1;
                    while (elapsed >= 1) {
                        --elapsed;
                        ++time;
                    }
                    world.setFullTime(time);
                    this.elapsed.put(world, elapsed);
                });
            }
        }, Ticks.MSPT));
    }

    @Override
    public void disable() {}
}