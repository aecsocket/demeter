package com.gitlab.aecsocket.natura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

public class NaturaPacketAdapter extends PacketAdapter {
    private final NaturaPlugin plugin;

    public NaturaPacketAdapter(NaturaPlugin plugin) {
        super(plugin, PacketType.Play.Server.LOGIN, PacketType.Play.Server.MAP_CHUNK);
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketType type = event.getPacketType();
        if (type == PacketType.Play.Server.LOGIN)
            plugin.features().forEach(f -> f.login(event));
        if (type == PacketType.Play.Server.MAP_CHUNK)
            plugin.features().forEach(f -> f.mapChunk(event));
    }
}
