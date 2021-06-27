package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import org.spongepowered.configurate.ConfigurateException;

public abstract class Feature<C> {
    protected final NaturaPlugin plugin;
    protected C config;

    public Feature(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public C config() { return config; }

    public abstract String id();

    public abstract void load() throws ConfigurateException;
    public abstract void enable();
    public abstract void disable();
}
