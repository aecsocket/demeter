package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public interface Feature extends Tickable {
    default void blockGrow(BlockGrowEvent event) {}
    default void mapChunk(PacketEvent event) {}
    default void itemConsume(PlayerItemConsumeEvent event) {}
}
