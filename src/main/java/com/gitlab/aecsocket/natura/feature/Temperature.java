package com.gitlab.aecsocket.natura.feature;

import com.destroystokyo.paper.MaterialSetTag;
import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector3I;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.*;

public class Temperature implements Feature {
    public interface Factor {
        double apply(Temperature feature, Player player, double temperature);
    }

    @ConfigSerializable
    public static class InbuiltFactors {
        @ConfigSerializable
        public static class Environment implements Factor {
            public final Map<World.Environment, Double> value = new HashMap<>();

            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                return temperature + value.getOrDefault(player.getWorld().getEnvironment(), 0d);
            }
        }

        @ConfigSerializable
        public static class Height implements Factor {
            public double baseline;
            public double factor;

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

            private boolean isWater(Block block) {
                Material material = block.getType();
                if (material == Material.WATER)
                    return true;
                if (MaterialSetTag.UNDERWATER_BONEMEALS.isTagged(material))
                    return true;
                BlockData data = block.getBlockData();
                if (data instanceof Waterlogged && ((Waterlogged) data).isWaterlogged())
                    return true;
                return false;
            }

            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                if (player.getFireTicks() > 0) {
                    temperature += onFire;
                }
                if (player.isSprinting()) {
                    temperature += sprinting;
                }
                if (
                        player.isSwimming()
                        || isWater(player.getLocation().getBlock())
                        || isWater(player.getEyeLocation().getBlock())
                ) {
                    temperature += inWater;
                }
                if (player.isSwimming()) {
                    temperature += swimming;
                }
                return temperature;
            }
        }

        @ConfigSerializable
        public static class BlockRelations implements Factor {
            public Vector3I radius;
            public final Map<Material, Double> blocks = new HashMap<>();

            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                Location location = player.getLocation();
                World world = location.getWorld();
                for (int x = location.getBlockX() - radius.x(); x < location.getBlockX() + radius.x(); x++) {
                    for (int y = location.getBlockY() - radius.y(); y < location.getBlockY() + radius.y(); y++) {
                        for (int z = location.getBlockZ() - radius.z(); z < location.getBlockZ() + radius.z(); z++) {
                            temperature += blocks.getOrDefault(world.getBlockAt(x, y, z).getType(), 0d);
                        }
                    }
                }
                return temperature;
            }
        }

        @ConfigSerializable
        public static class Armor implements Factor {
            public final Map<Material, Double> value = new HashMap<>();

            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                for (ItemStack armorItem : player.getInventory().getArmorContents()) {
                    if (armorItem == null)
                        continue;
                    temperature += value.getOrDefault(armorItem.getType(), 0d);
                }
                return temperature;
            }
        }

        @ConfigSerializable
        public static class Consumables implements Factor {
            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                return temperature;
            }
        }

        @ConfigSerializable
        public static class Season implements Factor {
            private final Map<String, Double> value = new HashMap<>();

            @Override
            public double apply(Temperature feature, Player player, double temperature) {
                Calendar calendar = feature.world.calendar();
                if (!calendar.settings().enabled)
                    return temperature;
                return temperature + value.getOrDefault(calendar.season().name, 0d);
            }
        }

        public Environment environment = new Environment();
        public Height height = new Height();
        public PlayerState playerState = new PlayerState();
        public Armor armor = new Armor();
        public BlockRelations blockRelations = new BlockRelations();
        public Consumables consumables = new Consumables();
        public Season season = new Season();
        public List<Factor> allFactors() {
            return Arrays.asList(environment, height, playerState, armor, blockRelations, consumables, season);
        }
    }

    @ConfigSerializable
    public static class Settings {
        public boolean enabled = true;
        public InbuiltFactors factors = new InbuiltFactors();
    }

    private final WorldData world;
    private final Settings settings;
    private final List<Factor> factors = new ArrayList<>();

    public Temperature(WorldData world, Settings settings) {
        this.world = world;
        this.settings = settings;
        factors.addAll(settings.factors.allFactors());
    }

    public WorldData world() { return world; }
    public Settings settings() { return settings; }
    public List<Factor> factors() { return factors; }

    public double baseTemperature(Player player) {
        Location location = player.getLocation();
        // 0 is "normal"
        // plains is 0.8, desert is 2.0, coldest is 0.0, so we subtract 1
        // base temperature
        return location.getWorld().getTemperature(
                location.getBlockX(),
                0,
                location.getBlockZ()
        ) - 1;
    }

    public double targetTemperature(Player player) {
        double temperature = baseTemperature(player);
        for (Factor factor : factors) {
            temperature = factor.apply(this, player, temperature);
        }
        return temperature;
    }

    @Override
    public void itemConsume(PlayerItemConsumeEvent event) {}

    @Override
    public void tick(TickContext tickContext) {}
}
