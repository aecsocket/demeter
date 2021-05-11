package com.gitlab.aecsocket.natura.feature;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector3I;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;
import java.util.function.BiPredicate;

public class BodyTemperature implements Feature {
    public static final String ID = "body_temperature";

    public interface Factor {
        double apply(BodyTemperature feature, Player player, double value);

        @ConfigSerializable
        class BlockClimate implements Factor {
            public double temperature;
            public double humidity;

            @Override
            public double apply(BodyTemperature feature, Player player, double value) {
                Climate climate = feature.plugin.climate();
                Block block = player.getLocation().getBlock();

                return value
                        + (climate.temperature(block) * temperature)
                        + (climate.humidity(block) * humidity);
            }
        }

        @ConfigSerializable
        final class BlockRelations implements Factor {
            @ConfigSerializable
            public static final class Relation {
                @Required public Vector3I radius;
                public Map<BlockData, Double> blocks = new HashMap<>();
            }

            @Setting(nodeFromParent = true)
            public List<Relation> relations = new ArrayList<>();

            @Override
            public double apply(BodyTemperature feature, Player player, double value) {
                World world = player.getWorld();
                Location location = player.getLocation();
                for (Relation relation : relations) {
                    Vector3I r = relation.radius;
                    for (int x = location.getBlockX() - r.x(); x < location.getBlockX() + r.x(); x++) {
                        for (int y = location.getBlockY() - r.y(); y < location.getBlockY() + r.y(); y++) {
                            for (int z = location.getBlockZ() - r.z(); z < location.getBlockZ() + r.z(); z++) {
                                if (!world.isChunkLoaded(x / 16, z / 16)) continue;
                                BlockData data = world.getBlockAt(x, y, z).getBlockData();
                                for (var entry : relation.blocks.entrySet()) {
                                    if (data.matches(entry.getKey()))
                                        value += entry.getValue();
                                }
                            }
                        }
                    }
                }
                return value;
            }
        }

        @ConfigSerializable
        final class PlayerState implements Factor {
            public double onFire;
            public double inWater;

            private boolean water(Block block) {
                if (block.getType() == Material.WATER) return true;
                BlockData data = block.getBlockData();
                if (data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged()) return true;
                return false;
            }

            @Override
            public double apply(BodyTemperature feature, Player player, double value) {
                if (player.getFireTicks() > 0)
                    value += onFire;
                Block block = player.getLocation().getBlock();
                if (player.isSwimming() || water(block) || water(block.getRelative(0, 1, 0)))
                    value += inWater;
                return value;
            }
        }

        @ConfigSerializable
        final class Armor implements Factor {
            @Setting(nodeFromParent = true)
            public Map<Material, Double> values = new HashMap<>();

            @Override
            public double apply(BodyTemperature feature, Player player, double value) {
                for (ItemStack item : player.getInventory().getArmorContents()) {
                    if (item == null) continue;
                    value += values.getOrDefault(item.getType(), 0d);
                }
                return value;
            }
        }

        @ConfigSerializable
        final class Consumables implements Factor {
            public double reduction;
            public Map<Material, Double> types = new HashMap<>();
            public Map<PotionType, Double> potions = new HashMap<>();
            public transient final Map<Player, Map<Object, Double>> effects = new HashMap<>();

            @Override
            public double apply(BodyTemperature feature, Player player, double value) {
                if (!effects.containsKey(player))
                    return value;
                for (var entry : effects.get(player).entrySet()) {
                    value += entry.getValue();
                }
                return value;
            }
        }
    }

    @ConfigSerializable
    public static final class Config {
        public boolean enabled = true;
        public long updateInterval = 1000;
        public long updateEffectsInterval = 5000;
        public double shiftMultiplier;
        public InbuiltFactors factors;
        public Effects effects;

        private void init(BodyTemperature feature) throws SerializationException {
            factors.init(feature);
        }
    }

    @ConfigSerializable
    public static final class InbuiltFactors {
        public Factor.BlockClimate climate;
        public Factor.BlockRelations blockRelations;
        public Factor.PlayerState playerState;
        public Factor.Armor armor;
        public Factor.Consumables consumables;

        private void init(BodyTemperature feature) throws SerializationException {
            feature.factors.clear();
            feature.factors.add(climate);
            feature.factors.add(blockRelations);
            feature.factors.add(playerState);
            feature.factors.add(armor);
            feature.factors.add(consumables);
        }
    }

    @ConfigSerializable
    public static final class Effects {
        public Map<Double, List<Effect>> higherThan = new HashMap<>();
        public Map<Double, List<Effect>> lowerThan = new HashMap<>();
    }

    @ConfigSerializable
    public static final class Effect {
        public List<PotionEffect> potionEffects = new ArrayList<>();
        public double damage;

        public void apply(Player player) {
            player.addPotionEffects(potionEffects);
            if (damage > 0) {
                player.damage(damage);
                player.setNoDamageTicks(0);
            }
        }
    }

    private final NaturaPlugin plugin;
    private Config config;
    private final List<Factor> factors = new ArrayList<>();
    private final Map<Player, Double> current = new HashMap<>();

    public BodyTemperature(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }
    public List<Factor> factors() { return factors; }
    public Map<Player, Double> current() { return current; }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        this.config.init(this);
    }

    public double current(Player player) { return current.computeIfAbsent(player, this::target); }
    public double target(Player player) {
        double value = 0;
        for (Factor factor : factors)
            value = factor.apply(this, player, value);
        return value;
    }

    private void applyEffects(Player player, double current, Map<Double, List<Effect>> effects, BiPredicate<Double, Double> test) {
        for (var entry : effects.entrySet()) {
            if (test.test(current, entry.getKey())) {
                for (Effect effect : entry.getValue()) {
                    effect.apply(player);
                }
            }
        }
    }

    @Override
    public void start() {
        if (!config.enabled) return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            current.entrySet().removeIf(e -> !e.getKey().isValid());

            Factor.Consumables consumables = config.factors.consumables;
            consumables.effects.entrySet().removeIf(e -> !e.getKey().isValid());

            for (Player player : Bukkit.getOnlinePlayers()) {
                double current = current(player);
                double target = target(player);
                current += (target - current) * config.shiftMultiplier;
                this.current.put(player, current);

                if (consumables.effects.containsKey(player)) {
                    var iter = consumables.effects.get(player).entrySet().iterator();
                    while (iter.hasNext()) {
                        var entry = iter.next();
                        double effect = entry.getValue();
                        double sign = Math.signum(effect);
                        effect -= consumables.reduction * (ctx.delta() / 1000d) * sign;
                        if ((sign == 0) || (sign > 0 && effect <= 0) || (sign < 0 && effect >= 0)) {
                            iter.remove();
                        } else {
                            consumables.effects.get(player).put(entry.getKey(), effect);
                        }
                    }
                }
            }
        }, config.updateInterval));

        plugin.scheduler().run(Task.repeating(ctx -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                double current = current(player);
                applyEffects(player, current, config.effects.higherThan, (a, b) -> a >= b);
                applyEffects(player, current, config.effects.lowerThan, (a, b) -> a <= b);
            }
        }, config.updateEffectsInterval));
    }

    @Override
    public void respawn(PlayerPostRespawnEvent event) {
        current.put(event.getPlayer(), target(event.getPlayer()));
    }

    @Override
    public void itemConsume(PlayerItemConsumeEvent event) {
        Factor.Consumables consumables = config.factors.consumables;
        Player player = event.getPlayer();
        Material material = event.getItem().getType();
        var effects = consumables.effects.computeIfAbsent(player, p -> new HashMap<>());

        effects.put(material, consumables.types.getOrDefault(material, 0d));
        ItemMeta meta = event.getItem().getItemMeta();
        if (meta instanceof PotionMeta) {
            PotionType type = ((PotionMeta) meta).getBasePotionData().getType();
            effects.put(type, consumables.potions.getOrDefault(type, 0d));
        }
    }
}
