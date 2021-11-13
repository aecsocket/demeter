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
import java.util.*;

public class TimeDilation extends Feature<TimeDilation.Config> {
    public static final String ID = "time_dilation";
    public static final int TICKS_PER_STAGE = Ticks.TPS * 60 * 10;
    public static final int TICKS_PER_DAY = TICKS_PER_STAGE * 2;
    public static final long MS_PER_STAGE = 1000 * 60 * 10;

    // Utils

    public record Factor(double value) {
        public static final Factor ONE = new Factor(1);

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

    public record CycleDuration(Factor day, Factor night, double ratio) {
        public static final CycleDuration ONE = new CycleDuration(Factor.ONE, Factor.ONE);

        public CycleDuration(Factor day, Factor night) {
            this(day, night, night.value / (day.value + night.value));
        }

        public Factor get(DayStage stage) {
            return stage == DayStage.NIGHT ? night : day;
        }

        public String asString(Locale locale) {
            return day.asDuration().asString(locale) + " | " + night.asDuration().asString(locale);
        }

        @Override
        public String toString() {
            return asString(Locale.ROOT);
        }

        public static final class Serializer implements TypeSerializer<CycleDuration> {
            @Override
            public void serialize(Type type, @Nullable CycleDuration obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    node.setList(Factor.class, Arrays.asList(obj.day, obj.night));
                }
            }

            @Override
            public CycleDuration deserialize(Type type, ConfigurationNode node) throws SerializationException {
                var nodes = node.childrenList();
                if (nodes.size() != 2)
                    throw new SerializationException(node, type, "Must be list of [ day duration, night duration ]");
                return new CycleDuration(
                        Serializers.require(nodes.get(0), Factor.class),
                        Serializers.require(nodes.get(1), Factor.class)
                );
            }
        }
    }

    // Config

    @ConfigSerializable
    public static final class Config {
        transient TimeDilation feature;
        public final @Required WorldsConfig<WorldConfig> worlds;

        private Config() {
            worlds = new WorldsConfig<>();
        }

        void initialize(TimeDilation feature) {
            this.feature = feature;
            for (var worldConfig : worlds.handle().values()) {
                worldConfig.initialize(this);
            }
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        transient Config config;
        public final CycleDuration duration;
        public final Map<String, CycleDuration> seasons;

        private WorldConfig() {
            duration = CycleDuration.ONE;
            seasons = new HashMap<>();
        }

        void initialize(Config config) {
            this.config = config;
        }

        public CycleDuration appliedDuration(World world) {
            return config.feature.plugin.seasons().standardSeason(world)
                    .flatMap(season -> Optional.ofNullable(seasons.get(season.name())))
                    .orElse(duration);
        }

        public double dayProgress(World world) {
            double x = world.getFullTime() % 24_000 / 24_000d;
            // GeoGebra formula: this line passes
            //  * (0, 0)
            //  * (0.5, h)
            //  * (1, 1)
            // f(x)=If(x<0.5, ((x)/(((0.5)/(h)))), (1-h)*2 (x-0.5)+h)
            // Here, h = n / (d + n), where:
            //  * d = day speed multiplier
            //  * n = night speed multiplier
            CycleDuration dur = appliedDuration(world);
            return x < 0.5
                    ? x / (0.5 / dur.ratio())
                    : (1 - dur.ratio()) * 2 * (x - 0.5) + dur.ratio();
        }
    }

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
        if (this.config == null)
            throw new SerializationException("null");
        this.config.initialize(this);
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
                    CycleDuration duration = worldConfig.appliedDuration(world);
                    double elapsed = this.elapsed.getOrDefault(world, 0d) + duration.get(dayStage(world)).value;
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
