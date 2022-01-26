package com.github.aecsocket.demeter.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

/* package */ final class DemeterListener implements Listener {
    private final DemeterPlugin plugin;

    public DemeterListener(DemeterPlugin plugin) {
        this.plugin = plugin;
    }

    private void forward(Consumer<Feature<?>> function) {
        plugin.features().forEach(function);
    }

    @EventHandler
    private void onEvent(PlayerQuitEvent event) {
        plugin.bossBars().remove(event.getPlayer());
    }

    @EventHandler
    private void onEvent(BlockGrowEvent event) {
        forward(f -> f.blockGrow(event));
    }
}
