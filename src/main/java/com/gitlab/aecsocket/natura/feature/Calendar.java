package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Calendar implements Feature {
    public static final int TICKS_PER_DAY = 20 * 60 * 20;

    @ConfigSerializable
    public static class Settings {
        public boolean enabled = true;
        public final List<Season> seasons = new ArrayList<>();

        @Override
        public String toString() {
            return "Settings{" +
                    "enabled=" + enabled +
                    ", seasons=" + seasons +
                    '}';
        }
    }

    @ConfigSerializable
    public static class Season {
        @ConfigSerializable
        public static class FertilitySettings {
            public double fertility = 1;
            public Double unaffectedY = null;
            public final List<Material> protectiveBlocks = new ArrayList<>();

            @Override
            public String toString() {
                return "FertilitySettings{" +
                        "fertility=" + fertility +
                        ", unaffectedY=" + unaffectedY +
                        ", protectiveBlocks=" + protectiveBlocks +
                        '}';
            }
        }

        @Required
        public String name;
        @Required
        public double duration = 30;
        public Integer biome;
        public FertilitySettings fertility = new FertilitySettings();

        public Component getLocalizedName(NaturaPlugin plugin, Locale locale) {
            return plugin.gen(locale, "season." + name);
        }

        @Override
        public String toString() {
            return "Season{" +
                    "name='" + name + '\'' +
                    ", duration=" + duration +
                    ", biome=" + biome +
                    ", fertility=" + fertility +
                    '}';
        }
    }

    @ConfigSerializable
    public static class State {
        public int seasonTicks;
        public int seasonIndex;
    }

    private final WorldData world;
    private final Settings settings;
    private final State state;
    private Season season;

    public Calendar(WorldData world, Settings settings, State state) {
        this.world = world;
        this.settings = settings;
        this.state = state;
        updateSeason();
    }

    public void updateSeason() {
        if (settings.seasons.size() == 0)
            return;
        season = settings.seasons.get(state.seasonIndex);

        // TODO: refresh client chunks on update
        // this is super hard to do without tons of hacks
    }

    public WorldData world() { return world; }
    public Settings settings() { return settings; }
    public State state() { return state; }
    public Season season() { return season; }

    @Override
    public void blockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        World bukkitWorld = block.getWorld();
        if (!world.world().equals(bukkitWorld))
            return;
        Season.FertilitySettings fertility = season.fertility;

        boolean affectedByFertility = true;
        if (fertility.unaffectedY != null) {
            for (int y = block.getY() + 1; y < bukkitWorld.getMaxHeight(); y++) {
                Material material = bukkitWorld.getBlockAt(block.getX(), y, block.getZ()).getType();
                if (
                        fertility.protectiveBlocks.contains(material)
                        || (block.getType() != Material.AIR && block.getY() <= fertility.unaffectedY)
                ) {
                    affectedByFertility = false;
                    break;
                }
            }
        }
        if (affectedByFertility && ThreadLocalRandom.current().nextDouble() > fertility.fertility) {
            event.setCancelled(true);
        }
    }

    @Override
    public void mapChunk(PacketEvent event) {
        if (season == null || season.biome == null)
            return;
        PacketContainer packet = event.getPacket();
        int[] biomes = new int[1024];
        // TODO leave alone the biomes which have special properties like deserts
        // only replace "grassy" biomes
        Arrays.fill(biomes, season.biome);
        packet.getIntegerArrays().write(0, biomes);
    }

    @Override
    public void tick(TickContext tickContext) {
        if (!settings.enabled) return;
        ++state.seasonTicks;
        if (state.seasonTicks > (season.duration * TICKS_PER_DAY)) {
            ++state.seasonIndex;
            state.seasonTicks = 0;
            if (state.seasonIndex >= settings.seasons.size()) {
                state.seasonIndex = 0;
            }
            updateSeason();
        }
    }
}
