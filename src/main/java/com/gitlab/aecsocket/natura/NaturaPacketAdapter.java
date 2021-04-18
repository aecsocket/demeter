package com.gitlab.aecsocket.natura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.feature.Feature;
import org.bukkit.entity.Player;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class NaturaPacketAdapter extends PacketAdapter {
    public NaturaPacketAdapter() {
        super(plugin(), PacketType.Play.Server.MAP_CHUNK);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketType type = event.getPacketType();
        PacketContainer packet = event.getPacket();
        Player player = event.getPlayer();

        if (type == PacketType.Play.Server.MAP_CHUNK) {
            for (Feature feature : plugin().features().values()) {
                feature.mapChunk(event);
            }
        }

        // TODO custom biomes
        /*if (type == PacketType.Play.Server.LOGIN) {
            // g = dimension codec
            IRegistryCustom.Dimension codec = (IRegistryCustom.Dimension) packet.getModifier().read(6);
            RegistryMaterials<BiomeBase> allBiomes = (RegistryMaterials<BiomeBase>) (RegistryMaterials) codec.b(ResourceKey.a(MinecraftKey.a("worldgen/biome")));
            BiomeBase plains = allBiomes.get(MinecraftKey.a("plains"));
            BiomeFog fog = plains.l();
            System.out.println("fog = " + fog);
            System.out.println(">  " + new ReflectionToStringBuilder(fog));
            try {
                Field foliageColor = fog.getClass().getDeclaredField("g");
                foliageColor.setAccessible(true);
                Optional<Integer> color = (Optional<Integer>) foliageColor.get(fog);
                System.out.println(">> foliage (g) = " + color);

                foliageColor.set(fog, Optional.of(16711680));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
            throw new RuntimeException("login");
        }*/

        /*
        if (type == PacketType.Play.Server.MAP_CHUNK) {
            WorldData data = plugin().worldData(player.getWorld());
            if (data == null)
                return;
        }*/
    }
}
