package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;

public class Weather implements Feature {
    public static final String ID = "weather";

    private final NaturaPlugin plugin;

    public Weather(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    @Override public String id() { return ID; }
}
