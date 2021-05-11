package com.gitlab.aecsocket.natura;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.gitlab.aecsocket.natura.feature.Feature;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

public class NaturaListener implements Listener {
    private final NaturaPlugin plugin;

    public NaturaListener(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    private void pass(Consumer<Feature> function) {
        for (Feature feature : plugin.features())
            function.accept(feature);
    }

    @EventHandler
    private void onEvent(PlayerJoinEvent event) {
        plugin.bossBar(event.getPlayer());
    }

    @EventHandler
    private void onEvent(PlayerQuitEvent event) {
        plugin.clearBossBar(event.getPlayer());
    }

    @EventHandler private void onEvent(PlayerPostRespawnEvent event) { pass(f -> f.respawn(event)); }
    @EventHandler private void onEvent(BlockGrowEvent event) { pass(f -> f.blockGrow(event)); }
    @EventHandler private void onEvent(PlayerItemConsumeEvent event) { pass(f -> f.itemConsume(event)); }
}
