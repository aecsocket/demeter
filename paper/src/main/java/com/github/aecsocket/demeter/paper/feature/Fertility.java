package com.github.aecsocket.demeter.paper.feature;

import com.destroystokyo.paper.MaterialTags;
import com.github.aecsocket.demeter.paper.DemeterPlugin;
import com.github.aecsocket.demeter.paper.Feature;
import com.github.aecsocket.demeter.paper.WorldsConfig;
import com.github.aecsocket.minecommons.core.Numbers;
import com.github.aecsocket.minecommons.core.Validation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockGrowEvent;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fertility extends Feature<Fertility.Config> {
    public static final String ID = "fertility";

    public static final String FACTOR_BASE = "base";
    public static final String FACTOR_WORLD_COVERED = "world_covered";
    public static final String FACTOR_WORLD_UNCOVERED = "world_uncovered";
    public static final String FACTOR_SEASON = "season";
    public static final String FACTOR_WORLD_TEMPERATURE = "world_temperature";
    public static final String FACTOR_WORLD_HUMIDITY = "world_humidity";
    public static final String FACTOR_CROP_TEMPERATURE = "crop_temperature";
    public static final String FACTOR_CROP_HUMIDITY = "crop_humidity";

    // Utils

    public record RangedFactor(
            double min,
            double optimal,
            double max,
            double multMin,
            double multMax
    ) {
        public RangedFactor {
            Validation.greaterThan("max", max, optimal);
            Validation.greaterThan("optimal", optimal, min);
            Validation.greaterThan("multMax", multMax, multMin);
        }

        public double calculate(double value) {
            if (value >= optimal)
                return Numbers.lerp(multMax, multMin, Numbers.clamp01(Math.abs(value - optimal) / (max - optimal)));
            else
                return Numbers.lerp(multMax, multMin, Numbers.clamp01(Math.abs(value - optimal) / (optimal - min)));
        }

        public static final class Serializer implements TypeSerializer<RangedFactor> {
            @Override
            public void serialize(Type type, @Nullable RangedFactor obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    ConfigurationNode range = node.appendListNode();
                    range.appendListNode().set(obj.min);
                    range.appendListNode().set(obj.optimal);
                    range.appendListNode().set(obj.max);

                    node.appendListNode().set(obj.multMin);
                    node.appendListNode().set(obj.multMax);
                }
            }

            @Override
            public RangedFactor deserialize(Type type, ConfigurationNode node) throws SerializationException {
                var nodes = node.childrenList();
                List<? extends ConfigurationNode> range;
                if (nodes.size() != 3 || (range = nodes.get(0).childrenList()).size() != 3)
                    throw new SerializationException(node, type, "Must be list of [ [ min, optimal, max ], multiplier min, multiplier max ]");
                try {
                    return new RangedFactor(
                            range.get(0).getDouble(),
                            range.get(1).getDouble(),
                            range.get(2).getDouble(),
                            nodes.get(1).getDouble(),
                            nodes.get(2).getDouble()
                    );
                } catch (IllegalArgumentException e) {
                    throw new SerializationException(node, type, e);
                }
            }
        }
    }

    // Config

    @ConfigSerializable
    public static final class ClimateFactors {
        public final @Nullable RangedFactor temperature;
        public final @Nullable RangedFactor humidity;

        private ClimateFactors() {
            temperature = null;
            humidity = null;
        }
    }

    @ConfigSerializable
    public static final class Config {
        transient Fertility feature;
        public final double baseChance;
        public final @Required WorldsConfig<WorldConfig> worlds;
        public final @Required Map<BlockData, ClimateFactors> crops;

        private Config() {
            baseChance = 1;
            worlds = new WorldsConfig<>();
            crops = new HashMap<>();
        }

        void initialize(Fertility feature) {
            this.feature = feature;
        }
    }

    @ConfigSerializable
    public static final class Multipliers {
        public final double chance;
        public final Map<String, Double> seasons;
        public final ClimateFactors climate;

        private Multipliers() {
            chance = 1;
            seasons = new HashMap<>();
            climate = new ClimateFactors();
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        public final int safeY;
        public final int coverHeight;
        public final Set<BlockData> coverBlocks;
        public final Multipliers covered;
        public final Multipliers uncovered;

        private WorldConfig() {
            safeY = 48;
            coverHeight = 16;
            coverBlocks = Stream.of(Material.values())
                    .filter(MaterialTags.GLASS::isTagged)
                    .map(Bukkit::createBlockData)
                    .collect(Collectors.toSet());
            covered = new Multipliers();
            uncovered = new Multipliers();
        }
    }

    public Fertility(DemeterPlugin plugin) {
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
    public void enable() {}

    @Override
    public void disable() {}

    public record FactorEntry(String key, double value) {}

    public List<FactorEntry> growthChanceFactors(Block block) {
        List<FactorEntry> factors = new ArrayList<>();
        factors.add(new FactorEntry(FACTOR_BASE, config.baseChance));

        World world = block.getWorld();
        config.worlds.get(world).ifPresent(worldConfig -> {
            boolean covered = false;
            int x = block.getX(), y = block.getY(), z = block.getZ();
            if (y <= worldConfig.safeY && world.getHighestBlockYAt(x, z) > y)
                covered = true;
            else {
                for (int d = 0; d < worldConfig.coverHeight; d++) {
                    Block above = world.getBlockAt(x, y + 1 + d, z);
                    for (var match : worldConfig.coverBlocks) {
                        if (above.getBlockData().matches(match)) {
                            covered = true;
                            break;
                        }
                    }
                    if (covered)
                        break;
                }
            }

            Multipliers multipliers = covered ? worldConfig.covered : worldConfig.uncovered;
            factors.add(new FactorEntry(covered ? FACTOR_WORLD_COVERED : FACTOR_WORLD_UNCOVERED, multipliers.chance));
            plugin.seasons().standardSeason(world).ifPresent(season -> {
                Double value = multipliers.seasons.get(season.name());
                if (value != null)
                    factors.add(new FactorEntry(FACTOR_SEASON, value));
            });

            Climate.State climate = plugin.climate().state(block);
            if (multipliers.climate.temperature != null)
                factors.add(new FactorEntry(FACTOR_WORLD_TEMPERATURE, multipliers.climate.temperature.calculate(climate.temperature())));
            if (multipliers.climate.humidity != null)
                factors.add(new FactorEntry(FACTOR_WORLD_HUMIDITY, multipliers.climate.humidity.calculate(climate.humidity())));
        });

        Climate.State state = plugin.climate().state(block);
        BlockData data = block.getBlockData();
        for (var entry : config.crops.entrySet()) {
            if (!data.matches(entry.getKey()))
                continue;
            var crop = entry.getValue();

            if (crop.temperature != null)
                factors.add(new FactorEntry(FACTOR_CROP_TEMPERATURE, crop.temperature.calculate(state.temperature())));
            if (crop.humidity != null)
                factors.add(new FactorEntry(FACTOR_CROP_HUMIDITY, crop.humidity.calculate(state.humidity())));
        }

        return factors;
    }

    public double growthChance(List<FactorEntry> factors) {
        double value = 1;
        for (var factor : factors) {
            value *= factor.value;
        }
        return value;
    }

    public double growthChance(Block block) {
        return growthChance(growthChanceFactors(block));
    }

    @Override
    public void blockGrow(BlockGrowEvent event) {
        double growChance = growthChance(event.getBlock());
        if (ThreadLocalRandom.current().nextDouble() >= growChance)
            event.setCancelled(true);
    }
}
