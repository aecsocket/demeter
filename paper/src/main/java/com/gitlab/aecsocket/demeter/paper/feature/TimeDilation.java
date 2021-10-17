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
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public class TimeDilation extends Feature<TimeDilation.Config> {
    public static final String ID = "time_dilation";

    @ConfigSerializable
    public record Config(
        WorldsConfig<WorldConfig> worlds
    ) {}

    public record Factor(double value) {
        public Duration asDuration() {
            return Duration.duration((long) (1_200_000 / value));
        }

        public static Factor fromDuration(Duration duration) {
            return new Factor(24_000 / (double) duration.ticks());
        }

        public static final class Serializer implements TypeSerializer<Factor> {
            @Override
            public void serialize(Type type, @Nullable Factor obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    node.set(obj.asDuration());
                }
            }

            @Override
            public Factor deserialize(Type type, ConfigurationNode node) throws SerializationException {
                return fromDuration(Serializers.require(node, Duration.class));
            }
        }
    }

    @ConfigSerializable
    public record WorldConfig(
        Factor duration
    ) {}

    private final Object2DoubleMap<World> elapsed = new Object2DoubleArrayMap<>();

    public TimeDilation(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

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
                    double elapsed = this.elapsed.getOrDefault(world, 0d) + worldConfig.duration.value;
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
    public void disable() {

    }
}
