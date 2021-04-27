package com.gitlab.aecsocket.natura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.feature.Feature;
import com.mojang.serialization.Lifecycle;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Arrays;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class NaturaPacketAdapter extends PacketAdapter {
    public NaturaPacketAdapter() {
        super(plugin(),
                PacketType.Play.Server.MAP_CHUNK,
                PacketType.Play.Server.LOGIN);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketType type = event.getPacketType();
        PacketContainer packet = event.getPacket();
        Player player = event.getPlayer();

        if (type == PacketType.Play.Server.MAP_CHUNK) {
            plugin().features().values().forEach(f -> f.mapChunk(event));
        }

        if (type == PacketType.Play.Server.LOGIN) {
            plugin().features().values().forEach(f -> f.login(event));
        }
    }
}
