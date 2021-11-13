package com.gitlab.aecsocket.demeter.paper.feature;

import com.destroystokyo.paper.MaterialTags;
import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;

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

    @ConfigSerializable
    public static final class Config {
        transient Fertility feature;
        public final double baseChance;
        public final @Required WorldsConfig<WorldConfig> worlds;

        private Config() {
            baseChance = 1;
            worlds = new WorldsConfig<>();
        }

        void initialize(Fertility feature) {}
    }

    @ConfigSerializable
    public static final class Multipliers {
        public final double chance;
        public final Map<String, Double> seasons;

        private Multipliers() {
            chance = 1;
            seasons = new HashMap<>();
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
        });

        /*plugin.climate().state(block).ifPresent(state -> {

        });*/

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
