package com.gitlab.aecsocket.demeter.paper.feature;

import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import com.gitlab.aecsocket.minecommons.core.biome.BiomeData;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Climate extends Feature<Climate.Config> {
    public static final String ID = "climate";

    public static final String FACTOR_BIOME = "biome";
    public static final String FACTOR_ALTITUDE = "altitude";

    // Utils

    @ConfigSerializable
    public record State(
            double temperature,
            double humidity
    ) {
        public State multiply(double v) {
            return new State(
                    temperature * v,
                    humidity * v
            );
        }
    }

    // Config

    @ConfigSerializable
    public static final class Config {
        transient Climate feature;
        public final @Required WorldsConfig<WorldConfig> worlds;

        private Config() {
            worlds = new WorldsConfig<>();
        }

        void initialize(Climate feature) {
            this.feature = feature;
        }
    }

    @ConfigSerializable
    public static final class Biomes {
        public final double temperatureMultiplier;
        public final double humidityMultiplier;

        private Biomes() {
            temperatureMultiplier = 1;
            humidityMultiplier = 0.5;
        }
    }

    @ConfigSerializable
    public static final class Altitude {
        public final double optimal;
        public final State multiplier;

        private Altitude() {
            optimal = 64;
            multiplier = new State(0.015, 0);
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        public final Biomes biomes;
        public final Altitude altitude;

        private WorldConfig() {
            biomes = new Biomes();
            altitude = new Altitude();
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

    public record FactorEntry(String key, State value) {}

    public List<FactorEntry> stateFactors(Block block) {
        World world = block.getWorld();
        List<FactorEntry> factors = new ArrayList<>();

        config.worlds.get(world).ifPresent(worldConfig -> {
            Optional.ofNullable(plugin.biomeInjector().get(block.getBiome().getKey())).ifPresent(entry -> {
                BiomeData biome = entry.biomeData();
                if (biome != null) {
                    factors.add(new FactorEntry(FACTOR_BIOME, new State(
                            (biome.temperature() - 0.5) * worldConfig.biomes.temperatureMultiplier,
                            biome.humidity() * worldConfig.biomes.humidityMultiplier
                    )));
                }
            });

            factors.add(new FactorEntry(FACTOR_ALTITUDE,
                    worldConfig.altitude.multiplier.multiply(worldConfig.altitude.optimal - block.getY())));
        });

        return factors;
    }

    public State state(List<FactorEntry> factors) {
        double temperature = 0, humidity = 0;
        for (var factor : factors) {
            temperature += factor.value.temperature;
            humidity += factor.value.humidity;
        }
        return new State(temperature, humidity);
    }

    public State state(Block block) {
        return state(stateFactors(block));
    }
}
