package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.unifiedframework.core.scheduler.Scheduler;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector3I;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.*;
import java.util.function.BiPredicate;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class Temperature implements Feature {
    public static final String ID = "temperature";
    public static final Type TYPE = (config, state) -> {
        Temperature feature = new Temperature(
                config.get(Config.class)
        );
        feature.config.init(feature);
        return feature;
    };

    public interface Factor {
        double apply(Temperature feature, Player player, double temperature);
    }

    @ConfigSerializable
    public static class Environment implements Factor {
        public Map<World.Environment, Double> types;
        public double precipitation;
        public double time;

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            temperature += types.getOrDefault(player.getWorld().getEnvironment(), 0d);
            Location location = player.getEyeLocation();
            World world = location.getWorld();
            if (!world.isClearWeather()) {
                if (world.getHighestBlockYAt(location) < location.getBlockY()) {
                    temperature += precipitation;
                }
            }
            if (world.getEnvironment() == World.Environment.NORMAL) {
                double ticksPerDay = plugin().ticksPerDay();
                temperature += Math.sin(((world.getFullTime() % ticksPerDay) / ticksPerDay) * 2 * Math.PI) * time;
            }
            return temperature;
        }
    }

    @ConfigSerializable
    public static class Height implements Factor {
        @Required public double baseline;
        @Required public double factor;

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            return temperature + ((baseline - player.getLocation().getY()) * factor);
        }
    }

    @ConfigSerializable
    public static class PlayerState implements Factor {
        public double onFire;
        public double sprinting;
        public double inWater;
        public double swimming;

        private boolean isWater(Location location) {
            Block block = location.getBlock();
            if (block.getType() == Material.WATER)
                return true;
            BlockData data = block.getBlockData();
            if (data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged())
                return true;
            return false;
        }

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            if (player.getFireTicks() > 0)
                temperature += onFire;
            if (player.isSprinting())
                temperature += sprinting;
            if (isWater(player.getLocation()) || isWater(player.getEyeLocation()))
                temperature += inWater;
            if (player.isSwimming())
                temperature += swimming;
            return temperature;
        }
    }

    @ConfigSerializable
    public static class Armor implements Factor {
        @Setting(nodeFromParent = true)
        public Map<Material, Double> values;

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item == null)
                    continue;
                temperature += values.getOrDefault(item.getType(), 0d);
            }
            return temperature;
        }
    }

    @ConfigSerializable
    public static class BlockRelations implements Factor {
        @ConfigSerializable
        public static class BlockRelation {
            public Vector3I radius;
            public Map<BlockData, Double> blocks;
        }

        @Setting(nodeFromParent = true)
        public List<BlockRelation> relations;

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            Location location = player.getLocation();
            World world = location.getWorld();
            for (BlockRelation relation : relations) {
                Vector3I radius = relation.radius;
                for (int x = location.getBlockX() - radius.x(); x < location.getBlockX() + radius.x(); x++) {
                    for (int y = location.getBlockY() - radius.y(); y < location.getBlockY() + radius.y(); y++) {
                        for (int z = location.getBlockZ() - radius.z(); z < location.getBlockZ() + radius.z(); z++) {
                            if (!world.isChunkLoaded(x / 16, z / 16))
                                continue;
                            BlockData data = world.getBlockAt(x, y, z).getBlockData();
                            for (var entry : relation.blocks.entrySet()) {
                                if (data.matches(entry.getKey())) {
                                    temperature += entry.getValue();
                                }
                            }
                        }
                    }
                }
            }
            return temperature;
        }
    }

    @ConfigSerializable
    public static class Season implements Factor {
        @Setting(nodeFromParent = true)
        public Map<String, Double> values;

        @Override
        public double apply(Temperature feature, Player player, double temperature) {
            Seasons seasons = plugin().feature(Seasons.ID);
            if (seasons == null)
                return temperature;
            Seasons.Season season = seasons.season(player);
            if (season == null)
                return temperature;
            return temperature + values.getOrDefault(season.name, 0d);
        }
    }

    @ConfigSerializable
    public static class InbuiltFactors {
        public Environment environment;
        public Height height;
        public PlayerState playerState;
        public Armor armor;
        public BlockRelations blockRelations;
        public Season season;
        public transient final List<Factor> factors = new ArrayList<>();

        private void init() {
            factors.addAll(Arrays.asList(environment, height, playerState, armor, blockRelations, season));
        }
    }

    @ConfigSerializable
    public static class Config {
        public InbuiltFactors factors;
        public WorldsConfig<WorldConfig> worlds;
        public Effects effects;
        @Required public double temperatureShift;
        @Required public long temperatureShiftInterval;
        @Required public long effectsInterval;

        private void init(Temperature feature) {
            factors.init();
            worlds.init();
            feature.factors.addAll(factors.factors);
        }
    }

    @ConfigSerializable
    public static class WorldConfig {
        public double base;
    }

    @ConfigSerializable
    public static class Effects {
        public Map<Double, Effect> higherThan;
        public Map<Double, Effect> lowerThan;
    }

    @ConfigSerializable
    public static class Effect {
        public Integer fire;
        public Double damage;
        public List<PotionEffect> effects;
    }

    private Config config;
    private final List<Factor> factors = new ArrayList<>();
    private final Map<Player, Double> temperature = new HashMap<>();

    public Temperature(Config config) {
        this.config = config;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public Object state() { return null; }
    public List<Factor> factors() { return factors; }
    public Map<Player, Double> temperature() { return temperature; }
    public double currentTemperature(Player player) { return temperature.computeIfAbsent(player, __ -> temperature(player)); }

    public double baseTemperature(Block block) {
        World world = block.getWorld();
        AtomicDouble base = new AtomicDouble();
        config.worlds.get(world).ifPresent(data -> base.set(data.base));
        double temperature = base.doubleValue();

        temperature += world.getTemperature(block.getX(), 0, block.getZ()) - 0.5;
        return temperature;
    }

    public double temperature(Player player) {
        double temperature = baseTemperature(player.getLocation().getBlock());
        for (Factor factor : factors) {
            temperature = factor.apply(this, player, temperature);
        }
        return temperature;
    }

    private void applyTemperature(Player player, double current, Map<Double, Effect> effects, BiPredicate<Double, Double> test) {
        for (var entry : effects.entrySet()) {
            if (test.test(current, entry.getKey())) {
                Effect effect = entry.getValue();
                if (effect.fire != null) {
                    int rFire = effect.fire / Utils.TPS;
                    if (player.getFireTicks() < rFire)
                        player.setFireTicks(rFire);
                }

                if (effect.damage != null) {
                    player.damage(effect.damage);
                    player.setNoDamageTicks(0);
                }

                if (effect.effects != null) {
                    player.addPotionEffects(effect.effects);
                }
            }
        }
    }

    @Override
    public void setUp(Scheduler scheduler) {
        scheduler.run(Task.repeating(ctx -> {
            temperature.entrySet().removeIf(entry -> !entry.getKey().isValid());

            for (Player player : Bukkit.getOnlinePlayers()) {
                double current = currentTemperature(player);
                double target = temperature(player);
                current += (target - current) * config.temperatureShift;
                temperature.put(player, current);
            }
        }, config.temperatureShiftInterval));

        scheduler.run(Task.repeating(ctx -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                double current = currentTemperature(player);
                applyTemperature(player, current, config.effects.higherThan, (a, b) -> a >= b);
                applyTemperature(player, current, config.effects.lowerThan, (a, b) -> a <= b);
            }
        }, config.effectsInterval));
    }
}
