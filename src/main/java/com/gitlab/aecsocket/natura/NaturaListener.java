package com.gitlab.aecsocket.natura;

import com.gitlab.aecsocket.natura.feature.Feature;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.function.Consumer;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class NaturaListener implements Listener {
    private void pass(Consumer<Feature> function) {
        for (Feature feature : plugin().features().values()) {
            function.accept(feature);
        }
    }

    @EventHandler public void onEvent(BlockGrowEvent event) { pass(f -> f.blockGrow(event)); }
    @EventHandler public void onEvent(TimeSkipEvent event) { pass(f -> f.timeSkip(event)); }
}
