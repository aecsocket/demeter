package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public interface Feature extends Tickable {
    interface Type {
        Feature load(ConfigurationNode config, ConfigurationNode state) throws SerializationException;
    }

    String id();
    Object state();

    default void blockGrow(BlockGrowEvent event) {}
    default void timeSkip(TimeSkipEvent event) {}
    default void mapChunk(PacketEvent event) {}
}
