package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.TimeUtils;
import com.gitlab.aecsocket.natura.util.WorldConfigManager;
import com.gitlab.aecsocket.unifiedframework.core.util.color.ColorModifier;
import com.gitlab.aecsocket.unifiedframework.core.util.color.RGBA;
import com.gitlab.aecsocket.unifiedframework.core.util.data.Tuple3;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Seasons implements Feature {
    public static final String ID = "seasons";

    @ConfigSerializable
    public static final class Config {
        public Map<String, Season> seasons;
        public WorldConfigManager<WorldConfig> worlds;

        private void init(Seasons feature) throws SerializationException {
            for (var entry : seasons.entrySet()) {
                Season season = entry.getValue();
                if (season.weight < 1)
                    throw new SerializationException("Season weight must be >= 1");
                season.name = entry.getKey();
            }

            for (var entry : worlds.worlds().entrySet()) {
                var cfg = entry.getValue();
                cfg.init(feature);
                if (cfg.timeCycleLength < 1) {
                    worlds.worlds().put(entry.getKey(), null);
                }
            }
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        public static final WorldConfig EMPTY = new WorldConfig();

        @Required public String cycleLength;
        public transient long timeCycleLength = 1;
        public List<BiomeConfig> biomes = new ArrayList<>();
        public transient Map<Biome, BiomeConfig> mappedBiomes = new HashMap<>();

        public BiomeConfig config(Biome biome) { return mappedBiomes.getOrDefault(biome, mappedBiomes.getOrDefault(null, BiomeConfig.EMPTY)); }

        private void init(Seasons feature) throws SerializationException {
            timeCycleLength = (long) TimeUtils.time(cycleLength).a().doubleValue();

            for (var cfg : biomes) {
                cfg.init(feature, this);
                if (cfg.biomes.size() == 0)
                    mappedBiomes.put(null, cfg);
                for (Biome biome : cfg.biomes)
                    mappedBiomes.put(biome, cfg);
            }
        }
    }

    @ConfigSerializable
    public static final class BiomeConfig {
        public static final BiomeConfig EMPTY = new BiomeConfig();

        @Required public List<Biome> biomes;
        @Setting(value = "seasons")
        @Required public List<String> seasonNames;
        public transient List<Season> seasons = new ArrayList<>();
        public transient Map<Season, Long> durations = new HashMap<>();

        public Season season(long time) {
            if (seasons.size() == 0)
                return null;

            long current = 0;
            for (Season season : seasons) {
                current += durations.get(season);
                if (current > time)
                    return season;
            }
            return null;
        }

        public Season byName(String name) {
            for (Season season : seasons) {
                if (season.name.equals(name))
                    return season;
            }
            return null;
        }

        public long startsAt(Season season) {
            long time = 0;
            for (Season current : seasons) {
                if (current.equals(season))
                    return time;
                time += durations.get(current);
            }
            return -1;
        }

        private void init(Seasons feature, WorldConfig worldConfig) throws SerializationException {
            int totalWeight = 0;
            for (String name : seasonNames) {
                Season season = feature.config.seasons.get(name);
                totalWeight += season.weight;
                seasons.add(season);
            }

            for (Season season : seasons) {
                durations.put(season, (long) (((double) season.weight / totalWeight) * worldConfig.timeCycleLength));
            }
        }
    }

    @ConfigSerializable
    public static final class Season {
        @ConfigSerializable
        public static final class Fertility {
            public double growthChance = 1;
            public double minY = 48;
            public List<BlockData> protectiveBlocks = new ArrayList<>();
            public int maxProtectHeight = 8;
        }

        public transient String name;
        public int weight = 1;
        @Required public TextColor color;
        public ColorModifier foliageColor;
        public ColorModifier grassColor;
        public BiomeBase.Precipitation precipitation;
        public Fertility fertility;

        public Component localizedName(NaturaPlugin plugin, Locale locale) {
            return plugin.gen(locale, "season." + name);
        }

        @Override public String toString() { return name; }
    }

    private final NaturaPlugin plugin;
    private Config config;
    private final Map<Season, Map<Integer, Integer>> biomeMappings = new HashMap<>();
    private final List<Tuple3<Integer, ResourceKey<BiomeBase>, BiomeBase>> injections = new ArrayList<>();

    public Seasons(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        this.config.init(this);
    }

    public long time(World world) { return (world.getFullTime() + 6000) % config(world).timeCycleLength; }
    public WorldConfig config(World world) { return config.worlds.config(world).orElse(WorldConfig.EMPTY); }
    public Season season(World world, Biome biome) { return config(world).config(biome).season(time(world)); }

    private Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        try {
            IRegistry<BiomeBase> biomeRegistry = RegistryGeneration.WORLDGEN_BIOME;
            var biomeIds = (Int2ObjectMap<ResourceKey<BiomeBase>>) getField(BiomeRegistry.class, "c").get(null);

            // 2. Collect every [default biome]
            Map<ResourceKey<BiomeBase>, BiomeBase> defaults = new HashMap<>();
            for (ResourceKey<BiomeBase> key : biomeIds.values()) {
                defaults.put(key, biomeRegistry.a(key));
            }

            // Set up reflection for step 3
            // Get all fields of BiomeFog
            Field fogFog = getField(BiomeFog.class, "b");
            Field fogWater = getField(BiomeFog.class, "c");
            Field fogWaterFog = getField(BiomeFog.class, "d");
            Field fogSky = getField(BiomeFog.class, "e");
            Field fogFoliage = getField(BiomeFog.class, "f");
            Field fogGrass = getField(BiomeFog.class, "g");
            Field fogGrassModifier = getField(BiomeFog.class, "h");
            Field fogParticle = getField(BiomeFog.class, "i");
            Field fogAmbientSound = getField(BiomeFog.class, "j");
            Field fogMoodSound = getField(BiomeFog.class, "k");
            Field fogAdditionsSound = getField(BiomeFog.class, "l");
            Field fogMusic = getField(BiomeFog.class, "m");

            // 3. For each [season]...
            for (Season season : config.seasons.values()) {
                biomeMappings.put(season, new HashMap<>());
                int id = 0;
                // For each [default biome]...
                for (var entry : defaults.entrySet()) {
                    // a. Create a copy of the [biome] with modified foliage colours
                    BiomeBase oldBiome = entry.getValue();
                    BiomeFog oldFog = oldBiome.l();

                    float temperature = oldBiome.k();
                    float rainfall = oldBiome.getHumidity();
                    OptionalInt foliage = OptionalInt.empty();
                    OptionalInt grass = OptionalInt.empty();
                    if (season.foliageColor != null) {
                        foliage = OptionalInt.of(
                                season.foliageColor.combine(RGBA.ofRGB(((Optional<Integer>) fogFoliage.get(oldFog))
                                        .orElseGet(() -> plugin.foliageColors().get(temperature, rainfall))))
                                        .rgbValue()
                        );
                    }
                    if (season.grassColor != null) {
                        grass = OptionalInt.of(
                                season.grassColor.combine(RGBA.ofRGB(((Optional<Integer>) fogGrass.get(oldFog))
                                        .orElseGet(() -> plugin.grassColors().get(temperature, rainfall))))
                                        .rgbValue()
                        );
                    }

                    BiomeFog.a newFogBuilder = new BiomeFog.a()
                            .a((int) fogFog.get(oldFog))
                            .b((int) fogWater.get(oldFog))
                            .c((int) fogWaterFog.get(oldFog))
                            .d((int) fogSky.get(oldFog))
                            .a((BiomeFog.GrassColor) fogGrassModifier.get(oldFog));
                    foliage.ifPresent(newFogBuilder::e);
                    grass.ifPresent(newFogBuilder::f);
                    ((Optional<BiomeParticles>) fogParticle.get(oldFog)).ifPresent(newFogBuilder::a);
                    ((Optional<SoundEffect>) fogAmbientSound.get(oldFog)).ifPresent(newFogBuilder::a);
                    ((Optional<CaveSoundSettings>) fogMoodSound.get(oldFog)).ifPresent(newFogBuilder::a);
                    ((Optional<CaveSound>) fogAdditionsSound.get(oldFog)).ifPresent(newFogBuilder::a);
                    ((Optional<Music>) fogMusic.get(oldFog)).ifPresent(newFogBuilder::a);
                    BiomeFog newFog = newFogBuilder.a();
                    // field "precipitation": only affects if precipitation is drawn at all
                    // -> rain/snow (e.g. plains/snowy_taiga) OR nothing (e.g. desert)
                    // field "temperature": dictated which type of precipitation
                    // -> rain: >= 0.15
                    // -> snow: < 0.15
                    BiomeBase newBiome = new BiomeBase.a()
                            .a(season.precipitation == null ? oldBiome.c() : season.precipitation) // a, precipitation
                            .a(oldBiome.t()) // b, geography/category
                            .a(oldBiome.h()) // c, depth
                            .b(oldBiome.j()) // d, scale
                            .c(season.precipitation == BiomeBase.Precipitation.SNOW ? 0f : 0.5f) // e, temperature
                            .a(BiomeBase.TemperatureModifier.NONE) // f, temperature modifier (cannot get this from a BiomeBase)
                            .d(oldBiome.getHumidity()) // g, downfall/humidity
                            .a(newFog) // h, special effects/fog (MODIFIED)
                            .a(oldBiome.b()) // i, settings mobs
                            .a(oldBiome.e()) // j, settings generation
                            .a();

                    // b. Adds the [new biome] as a custom biome in an empty ID slot
                    for (; biomeIds.containsKey(id); id++);
                    ResourceKey<BiomeBase> key = entry.getKey();
                    MinecraftKey location = key.a();
                    ResourceKey<BiomeBase> newKey = ResourceKey.a(IRegistry.ay, new MinecraftKey(location.getNamespace(), location.getKey() + "_natura_" + season.name));
                    injections.add(Tuple3.of(id, newKey, newBiome));
                    biomeIds.put(id, newKey);
                    biomeMappings.get(season).put(biomeRegistry.a(oldBiome), id);
                }
                plugin().log(LogLevel.VERBOSE, "Added biomes for season `%s` - last biome ID: %d", season.name, id);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin().log(LogLevel.WARN, e, "Could not set up season biomes");
        }
    }

    @Override
    public void stop() {
        try {
            @SuppressWarnings("unchecked")
            var biomeIds = (Int2ObjectMap<ResourceKey<BiomeBase>>) getField(BiomeRegistry.class, "c").get(null);
            int sizeBefore = biomeIds.size();
            biomeMappings.values().forEach(map -> map.values().forEach(_id -> {
                int id = _id;
                biomeIds.remove(id);
            }));
            plugin().log(LogLevel.VERBOSE, "Tore down biome registry: %d -> %d", sizeBefore, biomeIds.size());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin().log(LogLevel.WARN, e, "Could not tear down season biomes");
        }
    }

    @Override
    public void login(PacketEvent event) {
        PacketContainer packet = event.getPacket();

        IRegistryCustom.Dimension codec = (IRegistryCustom.Dimension) packet.getModifier().read(6);
        @SuppressWarnings({"unchecked", "rawtypes"})
        var biomeRegistry = (IRegistryWritable<BiomeBase>) (IRegistryWritable)
                codec.b(ResourceKey.a(MinecraftKey.a("worldgen/biome")));
        injections.forEach(i -> {
            ResourceKey<BiomeBase> key = i.b();
            BiomeBase value = i.c();
            if (!value.equals(biomeRegistry.a(key)))
                biomeRegistry.a(i.a(), key, value, Lifecycle.stable());
        });
    }

    @Override
    public void mapChunk(PacketEvent event) {
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();

        World world = player.getWorld();
        int[] biomes = packet.getIntegerArrays().read(0);
        long time = time(world);
        WorldConfig cfg = config(world);
        for (int i = 0; i < biomes.length; i++) {
            int id = biomes[i];
            Biome biome = plugin.biomeById(id);
            Season season = cfg.config(biome).season(time);
            if (season == null)
                continue;
            biomes[i] = biomeMappings.getOrDefault(season, Collections.emptyMap()).getOrDefault(id, id);
        }
    }

    @Override
    public void blockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        Season season = season(world, block.getBiome());
        if (season == null)
            return;
        int y = block.getY();
        if (y <= season.fertility.minY && world.getHighestBlockYAt(block.getX(), block.getZ()) > y)
            return;

        for (int i = 0; i < season.fertility.maxProtectHeight; i++) {
            Block current = block.getRelative(0, i, 0);
            for (BlockData data : season.fertility.protectiveBlocks) {
                if (current.getBlockData().matches(data))
                    return;
            }
            if (current.getType().isOccluding())
                break;
        }

        if (ThreadLocalRandom.current().nextDouble() > season.fertility.growthChance) {
            event.setCancelled(true);
        }
    }
}
