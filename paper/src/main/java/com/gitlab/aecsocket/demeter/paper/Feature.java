package com.gitlab.aecsocket.demeter.paper;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public abstract class Feature<C> {
    protected final DemeterPlugin plugin;
    protected C config;

    public Feature(DemeterPlugin plugin) {
        this.plugin = plugin;
    }

    public DemeterPlugin plugin() { return plugin; }
    public C config() { return config; }

    public abstract String id();

    public abstract void configure(ConfigurationNode config) throws SerializationException;
    public abstract void enable();
    public abstract void disable();
}
