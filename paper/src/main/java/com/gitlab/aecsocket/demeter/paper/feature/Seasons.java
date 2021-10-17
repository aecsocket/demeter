package com.gitlab.aecsocket.demeter.paper.feature;

import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

public class Seasons extends Feature<Seasons.Config> {
    public static final String ID = "seasons";

    @ConfigSerializable
    public record Config() {}

    public Seasons(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
    }

    @Override
    public void enable() {}

    @Override
    public void disable() {}
}
