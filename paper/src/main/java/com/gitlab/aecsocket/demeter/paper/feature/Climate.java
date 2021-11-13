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
import org.bukkit.block.Block;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.Optional;

public class Climate extends Feature<Climate.Config> {
    public static final String ID = "climate";

    // Utils

    public record State(
            double temperature,
            double humidity
    ) {}

    // Config

    @ConfigSerializable
    public static final class Config {
        transient Climate feature;

        private Config() {

        }

        void initialize(Climate feature) {

        }
    }

    public Climate(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

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
    }

    @Override
    public void disable() {}

    public Optional<State> state(World world, int x, int y, int z) {
        return Optional.of(new State(0, 0));
    }

    public Optional<State> state(Block block) {
        return state(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
