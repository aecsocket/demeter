package com.gitlab.aecsocket.natura;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.function.Consumer;

public class NaturaListener implements Listener {
    private final NaturaPlugin plugin;

    public NaturaListener(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    private void pass(World world, Consumer<WorldData> function) {
        WorldData data = plugin.worldData(world);
        if (data == null) {
            return;
        }
        function.accept(data);
    }

    @EventHandler
    public void onEvent(BlockGrowEvent event) {
        pass(event.getBlock().getWorld(), data -> data.blockGrow(event));
    }
}
