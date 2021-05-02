package com.gitlab.aecsocket.natura.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.GrassColors;
import com.gitlab.aecsocket.natura.util.ImageColors;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Scheduler;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.color.ColorModifier;
import com.gitlab.aecsocket.unifiedframework.core.util.color.RGBA;
import com.gitlab.aecsocket.unifiedframework.core.util.data.Tuple3;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.kyori.adventure.text.Component;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.*;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class Seasons implements Feature {
    public static final String PATH_FOLIAGE_COLORS = "foliage.png";
    public static final String PATH_GRASS_COLORS = "grass.png";
    public static final String ID = "seasons";
    public static final Type TYPE = (config, state) -> {
        Seasons feature = new Seasons(
                config.get(Config.class),
                state.get(State.class)
        );
        feature.config.init();
        feature.foliageColors = loadColors(PATH_FOLIAGE_COLORS, "foliage");
        feature.grassColors = new GrassColors(loadColors(PATH_GRASS_COLORS, "grass"));
        return feature;
    };

    private static ImageColors loadColors(String path, String resource) {
        try {
            return ImageColors.load(ImageIO.read(plugin().file(path)));
        } catch (IOException e) {
            throw new RuntimeException("Could not load " + resource + " colors from " + path, e);
        }
    }

    private static BlockFace[] checkDirections = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

    @ConfigSerializable
    public static class Config {
        @Required public double cycleDuration;
        @Required public long minBlockUpdateInterval;
        @Required public long maxBlockUpdateInterval;
        @Required public int blockUpdateMaxY;
        @Required public Map<String, Season> seasons;
        @Required public WorldsConfig<WorldConfig> worlds;

        private void init() {
            seasons.forEach((key, season) -> season.name = key);
            worlds.init();
            worlds.worlds().values().forEach(config -> config.init(seasons));
        }

        public Optional<WorldConfig> config(World world) { return worlds.get(world); }
    }

    @ConfigSerializable
    public static class Season {
        public transient String name;
        public Color color;
        public ColorModifier foliageColor;
        public ColorModifier grassColor;
        public BiomeBase.Precipitation precipitation;
        public int cycleWeight = 1;

        public double fertility = 1;
        public Integer cropSafeY;
        public List<Material> cropProtectiveBlocks = new ArrayList<>();
        public double freezeChance;
        public double thawChance;

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
    }

    @ConfigSerializable
    public static class State {
        public long cycleElapsed;
    }

    private Config config;
    private State state;
    private ImageColors foliageColors;
    private GrassColors grassColors;
    private int lastSkip = -1;
    private final Map<Integer, Biome> biomeIdToBiome = new HashMap<>();
    private final List<Tuple3<Integer, ResourceKey<BiomeBase>, BiomeBase>> customBiomes = new ArrayList<>();
    private final Map<Season, Map<Integer, Integer>> biomeMappings = new HashMap<>();
    private final Map<World, Map<Long, Long>> chunkUpdates = new HashMap<>();

    public Seasons(Config config, State state) {
        this.config = config;
        this.state = state;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public State state() { return state; }

    public ImageColors foliageColors() { return foliageColors; }
    public GrassColors grassColors() { return grassColors; }

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

    private Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Override
    public void tearDown() {
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
    @SuppressWarnings("unchecked")
    public void setUp(Scheduler scheduler) {
        try {
            IRegistry<BiomeBase> biomeRegistry = RegistryGeneration.WORLDGEN_BIOME;
            var biomeIds = (Int2ObjectMap<ResourceKey<BiomeBase>>) getField(BiomeRegistry.class, "c").get(null);

            // 1. Map [default biome IDs] to [Bukkit biomes]
            biomeIdToBiome.clear();
            biomeIds.forEach((id, key) -> biomeIdToBiome.put(id, CraftBlock.biomeBaseToBiome(biomeRegistry, biomeRegistry.a(key))));

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
                                        .orElseGet(() -> foliageColors.get(temperature, rainfall))))
                                        .rgbValue()
                        );
                    }
                    if (season.grassColor != null) {
                        grass = OptionalInt.of(
                                season.grassColor.combine(RGBA.ofRGB(((Optional<Integer>) fogGrass.get(oldFog))
                                        .orElseGet(() -> grassColors.get(temperature, rainfall))))
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
                    customBiomes.add(Tuple3.of(id, newKey, newBiome));
                    biomeIds.put(id, newKey);
                    biomeMappings.get(season).put(biomeRegistry.a(oldBiome), id);
                }
                plugin().log(LogLevel.VERBOSE, "Added biomes for season `%s` - last biome ID: %d", season.name, id);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin().log(LogLevel.WARN, e, "Could not set up season biomes");
        }

        scheduler.run(Task.repeating(ctx -> {
            ++state.cycleElapsed;
            long duration = cycleDuration();
            if (state.cycleElapsed >= duration) {
                state.cycleElapsed = state.cycleElapsed % duration;
            }
            if (state.cycleElapsed < 0) { // can happen if time skips
                state.cycleElapsed = 0;
            }
        }, Utils.MSPT));

        scheduler.run(Task.repeating(ctx -> {
            long time = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                Random rng = ThreadLocalRandom.current();
                Map<Long, Long> worldChunkUpdates = chunkUpdates.computeIfAbsent(world, __ -> new HashMap<>());
                for (Chunk chunk : world.getLoadedChunks()) {
                    long key = chunk.getChunkKey();
                    long nextUpdate = worldChunkUpdates.computeIfAbsent(key, __ -> time + blockUpdateInterval());
                    while (time >= nextUpdate) {
                        nextUpdate += blockUpdateInterval();
                        int x = (chunk.getX() * 16) + rng.nextInt(16);
                        int z = (chunk.getZ() * 16) + rng.nextInt(16);
                        Block block = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
                        if (block.getY() > config.blockUpdateMaxY)
                            break;
                        Season season = season(world, block.getBiome());
                        if (season == null)
                            break;
                        if (season.freezeChance > 0 && rng.nextDouble() <= season.freezeChance) {
                            if (block.getType() == Material.WATER) {
                                for (BlockFace face : checkDirections) {
                                    Block relative = block.getRelative(face);
                                    if (relative.getType().isOccluding()) {
                                        block.setType(Material.ICE);
                                        break;
                                    }
                                }
                            } else if (!world.isClearWeather() && block.getType().isOccluding()) {
                                block.getLocation().add(0, 1, 0).getBlock().setType(Material.SNOW);
                            }
                        } else if (season.thawChance > 0 && rng.nextDouble() <= season.thawChance) {
                            if (block.getType() == Material.SNOW) {
                                block.setType(Material.AIR);
                            } else if (block.getType() == Material.ICE) {
                                block.setType(Material.WATER);
                            }
                        }
                    }
                    worldChunkUpdates.put(key, nextUpdate);
                }
            }
        }, 500));
    }

    private long blockUpdateInterval() {
        return ThreadLocalRandom.current().nextLong(config.minBlockUpdateInterval, config.maxBlockUpdateInterval);
    }

    @Override
    public void login(PacketEvent event) {
        PacketContainer packet = event.getPacket();

        IRegistryCustom.Dimension codec = (IRegistryCustom.Dimension) packet.getModifier().read(6);
        @SuppressWarnings({"unchecked", "rawtypes"})
        var biomeRegistry = (IRegistryWritable<BiomeBase>) (IRegistryWritable)
                codec.b(ResourceKey.a(MinecraftKey.a("worldgen/biome")));
        customBiomes.forEach(d -> biomeRegistry.a(d.a(), d.b(), d.c(), Lifecycle.stable()));
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
        if (event.getSkipReason() == TimeSkipEvent.SkipReason.CUSTOM)
            return;
        if (lastSkip == Bukkit.getCurrentTick())
            return; // TODO bug where 3 time skip events fire at once
        state.cycleElapsed += (event.getSkipAmount() % NaturaPlugin.TICKS_PER_DAY) * plugin().dayLengthMultiplier();
        lastSkip = Bukkit.getCurrentTick();
    }

    @Override
    public void mapChunk(PacketEvent event) {
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();

        World world = player.getWorld();
        int[] biomes = packet.getIntegerArrays().read(0);
        for (int i = 0; i < biomes.length; i++) {
            int id = biomes[i];
            Biome biome = biomeIdToBiome.get(id);
            Season season = season(world, biome);
            if (season == null)
                continue;
            biomes[i] = biomeMappings.getOrDefault(season, Collections.emptyMap()).getOrDefault(id, id);
        }
    }
}
