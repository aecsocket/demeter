package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.minecommons.core.Ticks;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.serializers.Serializers;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.WorldConfigs;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.UUID;

public class TimeDilation extends Feature<TimeDilation.Config> {
    public static final String ID = "time_dilation";

    public record Factor(double value) {
        public static final int DAY_TIME = 24_000;
        public static final Factor DEFAULT = new Factor(DAY_TIME);

        public static final class Serializer implements TypeSerializer<Factor> {
            @Override
            public void serialize(Type type, Factor obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    node.set(obj.value);
                }
            }

            @Override
            public Factor deserialize(Type type, ConfigurationNode node) throws SerializationException {
                String arg = Serializers.require(node, String.class);
                if (arg.length() == 0)
                    throw new SerializationException(node, type, "Must be time dilation factor");

                char last = arg.charAt(arg.length() - 1);
                if (last == 'x' || last == '%') {
                    // relative
                    double value;
                    try {
                        value = Double.parseDouble(arg.substring(0, arg.length() - 1));
                    } catch (NumberFormatException e) {
                        throw new SerializationException(node, type, "Invalid number format for relative factor", e);
                    }
                    return new Factor(last == 'x' ? value : value / 100);
                }
                // absolute time
                try {
                    return new Factor((double) DAY_TIME / Ticks.ticks(arg));
                } catch (NumberFormatException e) {
                    throw new SerializationException(node, type, "Invalid absolute factor format", e);
                }
            }
        }
    }

    @ConfigSerializable
    public record Config(
            boolean enabled,
            WorldConfigs<WorldConfig> worlds
    ) {
        public static final Config DEFAULT = new Config(false, new WorldConfigs<>());
    }

    @ConfigSerializable
    public record WorldConfig(
            Factor factor
    ) {
        public static final WorldConfig DEFAULT = new WorldConfig(Factor.DEFAULT);
    }

    private final Object2DoubleMap<UUID> progress = new Object2DoubleArrayMap<>();

    public TimeDilation(NaturaPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    @Override
    public void load() throws ConfigurateException {
        config = plugin.setting(Config.DEFAULT, (n, d) -> n.get(Config.class, d), ID);
    }

    @Override
    public void enable() {
        if (!config.enabled)
            return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (World world : Bukkit.getWorlds()) {
                if (!world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))
                    continue;
                config.worlds.config(world).ifPresent(cfg -> {
                    if (cfg.factor.value == 1)
                        return;
                    double progress = this.progress.getOrDefault(world.getUID(), 0d) + cfg.factor.value;
                    long time = world.getFullTime();
                    while (progress >= 1) {
                        --progress;
                        ++time;
                    }
                    world.setFullTime(time);
                    this.progress.putIfAbsent(world.getUID(), progress);
                });
            }
        }, Ticks.MSPT));
    }

    @Override
    public void disable() {

    }
}
