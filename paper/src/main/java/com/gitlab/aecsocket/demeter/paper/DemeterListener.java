package com.gitlab.aecsocket.demeter.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/* package */ final class DemeterListener implements Listener {
    private final DemeterPlugin plugin;

    public DemeterListener(DemeterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onEvent(PlayerQuitEvent event) {
        plugin.bossBars().remove(event.getPlayer());
    }
}
