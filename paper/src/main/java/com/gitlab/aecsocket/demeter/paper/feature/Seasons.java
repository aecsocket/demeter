package com.gitlab.aecsocket.demeter.paper.feature;

import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.core.Precipitation;
import com.gitlab.aecsocket.minecommons.core.translation.Localizer;
import com.gitlab.aecsocket.minecommons.core.vector.cartesian.Vector3;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Biome;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Seasons extends Feature<Seasons.Config> {
    public static final String ID = "seasons";

    @ConfigSerializable
    public record Config(
            Map<String, Season> seasons,
            WorldsConfig<WorldConfig> worlds
    ) {}

    @ConfigSerializable
    public record Season(
            @Setting(nodeFromParent = true) String name,
            int weight,
            @Required Vector3 color,
            Vector3 foliageColor,
            Vector3 grassColor,
            Precipitation precipitation
    ) {
        public Component name(Localizer lc, Locale locale) {
            return lc.safe(locale, "season." + name);
        }
    }

    @ConfigSerializable
    public record WorldConfig(
            Duration cycleLength,
            List<BiomeConfig> biomes
    ) {}

    @ConfigSerializable
    public record BiomeConfig(
            List<Biome> biomes,
            List<String> seasons
    ) {}

    public Seasons(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
    }

    @Override
    public void enable() {

    }

    @Override
    public void disable() {}
}
