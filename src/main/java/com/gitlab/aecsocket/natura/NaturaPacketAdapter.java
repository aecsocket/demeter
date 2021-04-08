package com.gitlab.aecsocket.natura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

public class NaturaPacketAdapter extends PacketAdapter {
    private final NaturaPlugin plugin;

    public NaturaPacketAdapter(NaturaPlugin plugin) {
        super(plugin, PacketType.Play.Server.MAP_CHUNK);
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketType type = event.getPacketType();
        PacketContainer packet = event.getPacket();
        Player player = event.getPlayer();

        if (type == PacketType.Play.Server.MAP_CHUNK) {
            WorldData data = plugin.worldData(player.getWorld());
            if (data == null)
                return;
            data.mapChunk(event);
        }
    }
}
