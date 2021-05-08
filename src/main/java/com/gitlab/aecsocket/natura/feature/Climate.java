package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import org.bukkit.block.Block;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class Climate implements Feature {
    public static final String ID = "climate";

    @ConfigSerializable
    public static final class Config {
    }

    private final NaturaPlugin plugin;

    public Climate(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    @Override public String id() { return ID; }

    public double temperature(Block block) {
        double temperature = plugin.internalBiome(block.getBiome()).k() - 0.5; // 0 = neutral
        // TODO
        return temperature;
    }

    public double humidity(Block block) {
        double humidity = plugin.internalBiome(block.getBiome()).getHumidity();
        // TODO
        return humidity;
    }
}
