package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import net.kyori.adventure.text.Component;
import net.minecraft.server.v1_16_R3.BiomeBase;
import net.minecraft.server.v1_16_R3.BiomeStorage;
import net.minecraft.server.v1_16_R3.IRegistry;
import net.minecraft.server.v1_16_R3.PacketPlayOutMapChunk;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class Seasons implements Feature {
    public static final String ID = "seasons";
    public static final Type TYPE = (config, state) -> {
        Seasons feature = new Seasons(
                config.get(Config.class),
                state.get(State.class)
        );
        feature.config.init();
        return feature;
    };

    @ConfigSerializable
    public static class Config {
        @Required public double cycleDuration;
        @Required public Map<String, Season> seasons;
        @Required public WorldsConfig<WorldConfig> worlds;

        private void init() {
            seasons.forEach((key, season) -> season.name = key);
            worlds.init();
            worlds.worlds().values().forEach(config -> config.init(seasons));
        }

        public Optional<WorldConfig> config(World world) { return worlds.get(world); }

        @Override
        public String toString() {
            return "Config{" +
                    "cycleDuration=" + cycleDuration +
                    ", seasons=" + seasons +
                    ", worlds=" + worlds +
                    '}';
        }
    }

    @ConfigSerializable
    public static class Season {
        public transient String name;
        public Color color;
        public Integer biome;
        public int cycleWeight = 1;
        public double fertility = 1;
        public Integer cropSafeY;
        public List<Material> cropProtectiveBlocks = new ArrayList<>();

        public Component getLocalizedName(Locale locale) { return plugin().gen(locale, "season." + name); }

        @Override public String toString() { return name; }
    }

    @ConfigSerializable
    public static class WorldConfig {
        public boolean enabled = true;
        public List<BiomeData> biomes = new ArrayList<>();
        private transient final Map<Biome, BiomeData> mappedBiomes = new HashMap<>();

        private void init(Map<String, Season> seasons) {
            for (BiomeData data : biomes) {
                data.init(seasons);
                if (data.biomes.size() == 0) {
                    if (mappedBiomes.containsKey(null))
                        throw new IllegalArgumentException("Default biome data already supplied");
                    mappedBiomes.put(null, data);
                    continue;
                }

                for (Biome biome : data.biomes) {
                    if (mappedBiomes.containsKey(biome))
                        throw new IllegalArgumentException("Biome data already exists for " + biome.getKey());
                    mappedBiomes.put(biome, data);
                }
            }
        }

        public Optional<BiomeData> biomeData(Biome biome) {
            return Optional.ofNullable(mappedBiomes.getOrDefault(biome, mappedBiomes.get(null)));
        }

        @Override
        public String toString() {
            return "WorldConfig{" +
                    "enabled=" + enabled +
                    ", biomes=" + biomes +
                    '}';
        }
    }

    @ConfigSerializable
    public static class BiomeData {
        public List<Biome> biomes = new ArrayList<>();
        @Required public List<String> seasons = new ArrayList<>();
        public transient final List<Season> mappedSeasons = new ArrayList<>();
        public transient int totalWeight;
        public transient final Map<Season, Double> durationPortion = new HashMap<>();

        private void init(Map<String, Season> map) {
            for (String name : seasons) {
                Season season = map.get(name);
                if (season == null)
                    throw new IllegalArgumentException(String.format("Could not find season '%s'", name));
                mappedSeasons.add(season);
            }

            for (Season season : mappedSeasons) {
                totalWeight += season.cycleWeight;
            }
            for (Season season : mappedSeasons) {
                durationPortion.put(season, (double) season.cycleWeight / totalWeight);
            }
        }

        public Season currentSeason(double cycleComplete) {
            double currentPortion = 0;
            for (Season season : mappedSeasons) {
                double portion = durationPortion.get(season);
                currentPortion += portion;
                if (currentPortion >= cycleComplete)
                    return season;
            }
            return null;
        }

        @Override
        public String toString() {
            return "BiomeData{" +
                    "biomes=" + biomes +
                    ", seasons=" + seasons +
                    '}';
        }
    }

    @ConfigSerializable
    public static class State {
        public long cycleElapsed;

        @Override
        public String toString() {
            return "State{" +
                    "cycleElapsed=" + cycleElapsed +
                    '}';
        }
    }

    private Config config;
    private State state;
    private long lastTime = -1;

    public Seasons(Config config, State state) {
        this.config = config;
        this.state = state;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public State state() { return state; }

    public long cycleDuration() { return (long) (config.cycleDuration * plugin().ticksPerDay()); }
    public double cycleProgress() { return state.cycleElapsed / (double) cycleDuration(); }

    public Season season(World world, Biome biome) {
        AtomicReference<Season> result = new AtomicReference<>();
        config.config(world).flatMap(a -> a.biomeData(biome)).ifPresent(b ->
                result.set(b.currentSeason(cycleProgress())));
        return result.get();
    }
    public Season season(Location location) { return season(location.getWorld(), location.getBlock().getBiome()); }
    public Season season(Block block) { return season(block.getLocation()); }
    public Season season(Player player) { return season(player.getLocation()); }

    @Override
    public void tick(TickContext tickContext) {
        ++state.cycleElapsed;
        long duration = cycleDuration();
        if (state.cycleElapsed >= duration) {
            state.cycleElapsed = state.cycleElapsed % duration;
        }
        if (state.cycleElapsed < 0) { // can happen if time skips
            state.cycleElapsed = 0;
        }
    }

    @Override
    public void blockGrow(BlockGrowEvent event) {
        Location location = event.getBlock().getLocation();
        World world = location.getWorld();
                Season season = season(location);
        if (season == null)
            return;
        double fertility = season.fertility;
        if (fertility >= 1)
            return;

        boolean affected = true;
        for (int y = location.getBlockY() + 1; y < location.getWorld().getMaxHeight(); y++) {
            Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
            if (
                    (block.getType() != Material.AIR && season.cropSafeY != null && location.getBlockY() <= season.cropSafeY)
                    || season.cropProtectiveBlocks.contains(block.getType())
            ) {
                affected = false;
                break;
            }
        }

        if (affected && ThreadLocalRandom.current().nextDouble() > fertility) {
            event.setCancelled(true);
        }
    }

    @Override
    public void timeSkip(TimeSkipEvent event) {
        state.cycleElapsed += (event.getSkipAmount() % NaturaPlugin.TICKS_PER_DAY) * plugin().dayLengthMultiplier();
    }

    @Override
    public void mapChunk(PacketEvent event) {
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();
        World world = player.getWorld();
        double cycleProgress = cycleProgress();

        AtomicReference<Integer> biomeId = new AtomicReference<>();
        config.config(world).ifPresent(worldData -> {
            Biome biome = player.getLocation().getBlock().getBiome();
            worldData.biomeData(biome).ifPresent(biomeData -> {
                biomeId.set(biomeData.currentSeason(cycleProgress).biome);
            });
            /*
            BiomeStorage biomes = ((CraftChunk) world.getChunkAt(packet.getIntegers().read(0), packet.getIntegers().read(1))).getHandle().getBiomeIndex();
            IRegistry<BiomeBase> registry = (IRegistry<BiomeBase>) biomes.registry;
            Map<Integer, Integer> biomeMap = new HashMap<>();
            int[] biomeIds  = packet.getIntegerArrays().read(0);
            for (int i = 0; i < biomeIds.length; i++) {
                int biomeId = biomeIds[i];
                if (biomeMap.containsKey(biomeId)) {
                    biomeIds[i] = biomeMap.get(biomeId);
                    break;
                }

                Biome biome = CraftBlock.biomeBaseToBiome(registry, registry.fromId(biomeId));
                int fi = i;
                data.biomeData(biome).ifPresent(biomeData -> {
                    int mapped = biomes.registry.a(CraftBlock.biomeToBiomeBase(registry, biomeData.currentSeason(cycleProgress()).biome));
                    biomeMap.put(biomeId, mapped);
                    biomeIds[fi] = mapped;
                });
            }
            packet.getIntegerArrays().write(0, biomeIds);*/
        });
        if (biomeId.get() != null) {
            int[] biomes = new int[1024];
            Arrays.fill(biomes, biomeId.get());
            packet.getIntegerArrays().write(0, biomes);
        }
    }
}
