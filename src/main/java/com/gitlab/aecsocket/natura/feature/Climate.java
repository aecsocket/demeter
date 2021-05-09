package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.WorldConfigManager;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2D;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2I;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.noise.NoiseGenerator;
import org.bukkit.util.noise.PerlinNoiseGenerator;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Climate implements Feature {
    public static final String ID = "climate";

    @ConfigSerializable
    public static final class Config {
        public boolean enabled = true;
        public long updateInterval = 10000;
        public double temperatureMultiplier = 1;
        public double humidityMultiplier = 1;
        public WorldConfigManager<WorldConfig> worlds;
    }

    @ConfigSerializable
    public static final class NoiseSettings {
        public double temperatureZ = -1024;
        public double humidityZ = 1024;
        public int octaves = 4;
        public double frequency = 0;
        public double amplitude = 0;
        public double scale = 1;

        public double noise(NoiseGenerator noise, double x, double y, double z) {
            return noise.noise(x / scale, y / scale, z / scale, octaves, frequency, amplitude, true);
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        public static final WorldConfig EMPTY = new WorldConfig();

        @Required public boolean enabled;
        public Vector2D defaultWind;
        public NoiseSettings noiseSettings;
    }

    @ConfigSerializable
    public static class WorldState {
        public static final WorldState EMPTY = new WorldState(null) {
            @Override protected double val(Vector2I pos, double z, Map<Vector2I, Double> cache) { return 0; }
        };

        public transient final World world;
        public Vector2D wind = new Vector2D();
        public List<Vector2D> forecast = new ArrayList<>();
        public Vector2D noiseOffset = new Vector2D();
        public transient NoiseGenerator noise;
        public transient NoiseSettings noiseSettings;
        public transient final Map<Vector2I, Double> temperatureCache = new HashMap<>();
        public transient final Map<Vector2I, Double> humidityCache = new HashMap<>();

        public WorldState(World world) {
            this.world = world;
        }

        protected double val(Vector2I pos, double z, Map<Vector2I, Double> cache) {
            if (cache.containsKey(pos))
                return cache.get(pos);
            double result = noiseSettings.noise(noise,
                    pos.x() + noiseOffset.x(),
                    pos.y() + noiseOffset.y(),
                    z
            );
            cache.put(pos, result);
            return result;
        }

        public double temperature(Vector2I pos) { return val(pos, noiseSettings.temperatureZ, temperatureCache); }
        public double temperature(int x, int y) { return temperature(new Vector2I(x, y)); }

        public double humidity(Vector2I pos) { return val(pos, noiseSettings.humidityZ, humidityCache); }
        public double humidity(int x, int y) { return humidity(new Vector2I(x, y)); }

        public void update(long delta) {
            if (wind.manhattanLength() > 0d) {
                noiseOffset = noiseOffset.add(wind.multiply(delta / 1000d));
                temperatureCache.clear();
                humidityCache.clear();
            }
        }

        public static WorldState of(World world, WorldConfig cfg) {
            WorldState r = new WorldState(world);
            r.noise = new PerlinNoiseGenerator(world.getSeed());
            r.noiseSettings = cfg.noiseSettings;
            Random rng = ThreadLocalRandom.current();
            r.wind = cfg.defaultWind == null ? new Vector2D(rng.nextGaussian(), rng.nextGaussian()) : cfg.defaultWind;
            return r;
        }
    }

    private final NaturaPlugin plugin;
    private Config config;
    private Map<World, WorldState> worlds = new HashMap<>();

    public Climate(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }
    public Map<World, WorldState> worlds() { return worlds; }
    public WorldState state(World world) {
        return worlds.computeIfAbsent(world, w -> {
            WorldConfig cfg = config(w);
            if (cfg.enabled)
                return WorldState.of(w, cfg);
            return WorldState.EMPTY;
        });
    }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        worlds.clear();
    }

    public WorldConfig config(World world) { return config.worlds.config(world).orElse(WorldConfig.EMPTY); }

    public double baseTemperature(Block block) { return plugin.internalBiome(block.getBiome()).k() - 0.5; /* 0 = neutral */ }
    public double baseHumidity(Block block) { return plugin.internalBiome(block.getBiome()).getHumidity(); }

    public double temperature(Block block) {
        return Utils.clamp(
                baseTemperature(block) + (state(block.getWorld()).temperature(block.getX(), block.getZ()) * config.temperatureMultiplier),
                -1, 1
        );
    }

    public double humidity(Block block) {
        return Utils.clamp01(
                baseHumidity(block) + (state(block.getWorld()).humidity(block.getX(), block.getZ()) * config.humidityMultiplier)
        );
    }

    @Override
    public void start() {
        if (!config.enabled) return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (World world : Bukkit.getWorlds()) {
                WorldConfig cfg = config(world);
                if (!cfg.enabled) continue;

                WorldState state = state(world);
                state.update(ctx.delta());
            }
        }, config.updateInterval));
    }
}
