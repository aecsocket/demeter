package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.WorldConfigManager;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2D;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2I;
import net.minecraft.server.v1_16_R3.BiomeBase;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.noise.NoiseGenerator;
import org.bukkit.util.noise.PerlinNoiseGenerator;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Climate implements Feature {
    public static final String ID = "climate";

    public interface Factor {
        double temperature(Climate feature, Block block, double value);
        double humidity(Climate feature, Block block, double value);

        @ConfigSerializable
        final class Biome implements Factor {
            public Vector2D multiplier = Vector2D.ZERO;

            @Override
            public double temperature(Climate feature, Block block, double value) {
                BiomeBase biome = feature.plugin.internalBiome(block.getBiome());
                if (biome == null)
                    return value;
                return value
                        + ((biome.k() - 0.5) * multiplier.x()); // our neutral = 0, MC neutral = 0.5
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                BiomeBase biome = feature.plugin.internalBiome(block.getBiome());
                if (biome == null)
                    return value;
                return value
                        + (biome.getHumidity() * multiplier.y());
            }
        }

        @ConfigSerializable
        final class Altitude implements Factor {
            public double base = 64;
            public Vector2D multiplier = Vector2D.ZERO;

            @Override
            public double temperature(Climate feature, Block block, double value) {
                return value + (base - block.getY()) * multiplier.x();
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                return value + (base - block.getY()) * multiplier.y();
            }
        }

        @ConfigSerializable
        final class Environment implements Factor {
            public Map<World.Environment, Vector2D> environment = new HashMap<>();
            public Vector2D raining = new Vector2D();
            public Vector2D rainingNotProtected = new Vector2D();

            @Override
            public double temperature(Climate feature, Block block, double value) {
                World world = block.getWorld();
                value += environment.getOrDefault(world.getEnvironment(), Vector2D.ZERO).x();
                if (!world.isClearWeather()) {
                    value += raining.x();
                    if (world.getHighestBlockYAt(block.getX(), block.getZ()) <= block.getY())
                        value += rainingNotProtected.x();
                }
                return value;
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                World world = block.getWorld();
                value += environment.getOrDefault(world.getEnvironment(), Vector2D.ZERO).y();
                if (!world.isClearWeather()) {
                    value += raining.y();
                    if (world.getHighestBlockYAt(block.getX(), block.getZ()) >= block.getY())
                        value += rainingNotProtected.y();
                }
                return value;
            }
        }

        @ConfigSerializable
        final class Season implements Factor {
            @Setting(nodeFromParent = true)
            public Map<String, Vector2D> values = new HashMap<>();

            @Override
            public double temperature(Climate feature, Block block, double value) {
                Seasons.Season season = feature.plugin.seasons().season(block.getWorld(), block.getBiome());
                return value + values.getOrDefault(season.name, Vector2D.ZERO).x();
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                Seasons.Season season = feature.plugin.seasons().season(block.getWorld(), block.getBiome());
                return value + values.getOrDefault(season.name, Vector2D.ZERO).y();
            }
        }

        @ConfigSerializable
        final class TimeOfDay implements Factor {
            public Vector2D multiplier = Vector2D.ZERO;

            @Override
            public double temperature(Climate feature, Block block, double value) {
                World world = block.getWorld();
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    return value + (Math.sin((world.getTime() / 24000d) * 2 * Math.PI) * multiplier.x());
                }
                return value;
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                World world = block.getWorld();
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    return value + (Math.sin((world.getTime() / 24000d) * 2 * Math.PI) * multiplier.y());
                }
                return value;
            }
        }

        @ConfigSerializable
        final class WindNoise implements Factor {
            public Vector2D multiplier = Vector2D.ZERO;

            @Override
            public double temperature(Climate feature, Block block, double value) {
                return value + (feature.state(block.getWorld()).temperature(block.getX(), block.getZ()) * multiplier.x());
            }

            @Override
            public double humidity(Climate feature, Block block, double value) {
                return value + (feature.state(block.getWorld()).humidity(block.getX(), block.getZ()) * multiplier.y());
            }
        }
    }

    @ConfigSerializable
    public static final class Config {
        public boolean enabled = true;
        public long updateInterval = 10000;
        public WorldConfigManager<WorldConfig> worlds;
        public InbuiltFactors factors;

        private void init(Climate feature) throws SerializationException {
            feature.state
                    .clear();
            factors.init(feature);
        }
    }

    @ConfigSerializable
    public static final class InbuiltFactors {
        public Factor.Biome biome;
        public Factor.Altitude altitude;
        public Factor.Environment environment;
        public Factor.Season season;
        public Factor.TimeOfDay timeOfDay;
        public Factor.WindNoise windNoise;

        private void init(Climate feature) throws SerializationException {
            feature.factors.clear();
            feature.factors.add(biome);
            feature.factors.add(altitude);
            feature.factors.add(environment);
            feature.factors.add(season);
            feature.factors.add(timeOfDay);
            feature.factors.add(windNoise);
        }
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
    private List<Factor> factors = new ArrayList<>();
    private transient Map<World, WorldState> state = new HashMap<>();

    public Climate(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }
    public List<Factor> factors() { return factors; }
    public Map<World, WorldState> state() { return state; }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        this.config.init(this);
    }

    public WorldConfig config(World world) { return config.worlds.config(world).orElse(WorldConfig.EMPTY); }

    public WorldState state(World world) {
        return state.computeIfAbsent(world, w -> {
            WorldConfig cfg = config(w);
            if (cfg.enabled)
                return WorldState.of(w, cfg);
            return WorldState.EMPTY;
        });
    }

    public double temperature(Block block) {
        double value = 0;
        for (Factor factor : factors)
            value = factor.temperature(this, block, value);
        return value;
    }

    public double humidity(Block block) {
        double value = 0;
        for (Factor factor : factors)
            value = factor.humidity(this, block, value);
        return Utils.clamp01(value);
    }

    @Override
    public void start() {
        if (!config.enabled) return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (World world : Bukkit.getWorlds()) {
                WorldConfig cfg = config.worlds.config(world).orElse(WorldConfig.EMPTY);
                if (!cfg.enabled) continue;

                WorldState state = state(world);
                state.update(ctx.delta());
            }
        }, config.updateInterval));
    }
}
