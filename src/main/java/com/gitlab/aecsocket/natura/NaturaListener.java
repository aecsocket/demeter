package com.gitlab.aecsocket.natura;

import org.bukkit.event.Listener;

/* package */ class NaturaListener implements Listener {
    private final NaturaPlugin plugin;

    public NaturaListener(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
}
