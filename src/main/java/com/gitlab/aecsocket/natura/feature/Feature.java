package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public interface Feature {
    String id();

    default void acceptConfig(ConfigurationNode config) throws SerializationException {}
    default void acceptState(ConfigurationNode state) throws SerializationException {}

    default void start() {}
    default void stop() {}

    default void login(PacketEvent event) {}
    default void mapChunk(PacketEvent event) {}
    default void respawn(PlayerPostRespawnEvent event) {}
    default void blockGrow(BlockGrowEvent event) {}
}
