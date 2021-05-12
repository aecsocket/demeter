package com.gitlab.aecsocket.natura.util;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import net.minecraft.server.v1_16_R3.PacketPlayOutGameStateChange;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class NaturaProtocol {
    private NaturaProtocol() {}

    public static void downfall(NaturaPlugin plugin, Player player, double downfall) {
        // TODO convert to protocollib
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutGameStateChange(
                PacketPlayOutGameStateChange.h, (float) downfall
        ));
    }
}
