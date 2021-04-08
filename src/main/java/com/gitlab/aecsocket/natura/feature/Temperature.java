package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Temperature implements Feature {
    public interface Factor {
        double apply(Player player, double temperature);
    }

    @ConfigSerializable
    public static class InbuiltFactors {
        @ConfigSerializable
        public static class Height implements Factor {
            public double baseline;
            public double factor;

            @Override
            public double apply(Player player, double temperature) {
                return temperature + ((baseline - player.getLocation().getY()) * factor);
            }
        }

        @ConfigSerializable
        public static class Armor implements Factor {
            public double factor;

            @Override
            public double apply(Player player, double temperature) {
                return temperature + (player.getAttribute(Attribute.GENERIC_ARMOR).getValue() * factor);
            }
        }

        public Height height = new Height();
        public Armor armor = new Armor();
        public List<Factor> allFactors() {
            return Arrays.asList(height, armor);
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

    public double getTargetTemperature(Player player) {
        Location location = player.getLocation();
        // 0 is "normal"
        // plains is 0.8, desert is 2.0, coldest is 0.0, so we subtract 1
        // base temperature
        double temperature = location.getWorld().getTemperature(
                location.getBlockX(),
                0,
                location.getBlockZ()
        ) - 1;

        for (Factor factor : factors) {
            temperature = factor.apply(player, temperature);
        }

        return temperature;
    }

    @Override
    public void tick(TickContext tickContext) {
        if (!settings.enabled) return;
        for (Player player : world.world().getPlayers()) {
            player.sendActionBar(Component.text("temperature = " + String.format("%.03f", getTargetTemperature(player))));
        }
    }
}
