package com.gitlab.aecsocket.demeter.paper.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.core.Logging;
import com.gitlab.aecsocket.minecommons.core.biome.Precipitation;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.serializers.Serializers;
import com.gitlab.aecsocket.minecommons.core.translation.Localizer;
import com.gitlab.aecsocket.minecommons.core.vector.cartesian.Vector3;
import com.gitlab.aecsocket.minecommons.paper.MinecommonsPlugin;
import com.gitlab.aecsocket.minecommons.paper.biome.BiomeInjector;
import com.gitlab.aecsocket.minecommons.paper.biome.PaperBiomeData;
import com.gitlab.aecsocket.minecommons.paper.biome.PaperBiomeEffects;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.NodeKey;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.*;

public class Seasons extends Feature<Seasons.Config> {
    public static final String ID = "seasons";
    private static final Int2IntMap emptyInt2IntMap = new Int2IntArrayMap();

    @ConfigSerializable
    public static final class Config {
        public final @Required Map<String, Season> seasons;
        public final @Required WorldsConfig<WorldConfig> worlds;
        public final boolean obfuscateBiomeKeys;
        public final @Nullable Duration biomeUpdateInterval;

        public Config(Map<String, Season> seasons, WorldsConfig<WorldConfig> worlds, boolean obfuscateBiomeKeys, @Nullable Duration biomeUpdateInterval) {
            this.seasons = seasons;
            this.worlds = worlds;
            this.obfuscateBiomeKeys = obfuscateBiomeKeys;
            this.biomeUpdateInterval = biomeUpdateInterval;
        }

        private Config() {
            this(null, null, false, Duration.duration(1000 * 60));
        }

        void initialize() {
            for (var entry : worlds) {
                try {
                    entry.getValue().initialize(this);
                } catch (Exception e) {
                    throw new RuntimeException("Could not initialize config for world `" + entry.getKey() + "`", e);
                }
            }
        }
    }

    public static final class ColorModifier {
        private final Vector3 value;
        private final boolean hsv;
        private final double alpha;

        private ColorModifier(Vector3 value, boolean hsv, double alpha) {
            this.value = value;
            this.hsv = hsv;
            this.alpha = alpha;
        }

        public static ColorModifier hsv(Vector3 value) { return new ColorModifier(value, true, 0); }
        public static ColorModifier rgba(Vector3 value, double alpha) { return new ColorModifier(value, false, alpha); }

        public Vector3 value() { return value; }
        public boolean hsv() { return hsv; }
        public double alpha() { return alpha; }

        public Vector3 combineOn(Vector3 baseRgb) {
            if (hsv)
                return baseRgb.rgbToHsv().add(value).hsvToRgb();
            else
                return baseRgb.lerp(value, alpha);
        }

        public static final class Serializer implements TypeSerializer<ColorModifier> {
            public static final String HSV = "hsv";
            public static final String RGBA = "rgba";

            @Override
            public void serialize(Type type, @Nullable ColorModifier obj, ConfigurationNode node) throws SerializationException {
                if (obj == null) node.set(null);
                else {
                    if (obj.hsv) {
                        node.appendListNode().set(HSV);
                        node.appendListNode().set(obj.value);
                    } else {
                        node.appendListNode().set(RGBA);
                        node.appendListNode().set(obj.value);
                        node.appendListNode().set(obj.alpha);
                    }
                }
            }

            @Override
            public ColorModifier deserialize(Type type, ConfigurationNode node) throws SerializationException {
                var nodes = node.childrenList();
                if (nodes.size() < 1)
                    throw new SerializationException(node, type, "Must be a list starting with first element of either `" + HSV + "` or `" + RGBA + "`");
                String op = Serializers.require(nodes.get(0), String.class);
                switch (op) {
                    case HSV -> {
                        if (nodes.size() < 2)
                            throw new SerializationException(node, type, "Must be list of [ 'hsv', HSV color modifier ]");
                        return ColorModifier.hsv(Serializers.require(nodes.get(1), Vector3.class));
                    }
                    case RGBA -> {
                        if (nodes.size() < 3)
                            throw new SerializationException(node, type, "Must be list of [ 'rgba', RGB value, alpha value ]");
                        return ColorModifier.rgba(
                                Serializers.require(nodes.get(1), Vector3.class),
                                nodes.get(2).getDouble()
                        );
                    }
                }
                throw new SerializationException(node, type, "Unknown color modifier type `" + op + "`");
            }
        }
    }

    @ConfigSerializable
    public record Season(
            @NodeKey String name,
            @Required int weight,
            @Required Vector3 color,
            @Nullable ColorModifier foliageColor,
            @Nullable ColorModifier grassColor,
            @Nullable ColorModifier fogColor,
            @Nullable ColorModifier skyColor,
            @Nullable ColorModifier waterColor,
            @Nullable ColorModifier waterFogColor,
            @Nullable Precipitation precipitation
    ) {
        public Component name(Localizer lc, Locale locale) {
            return lc.safe(locale, "season." + name);
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        public final @Required Duration cycleLength;
        public transient long cycleLengthTicks;
        public final @Required List<BiomeConfig> biomes;
        public transient @Nullable BiomeConfig defaultBiome;
        public transient final Map<Key, Optional<BiomeConfig>> mappedBiomes = new HashMap<>();

        public WorldConfig(Duration cycleLength, List<BiomeConfig> biomes) {
            this.cycleLength = cycleLength;
            this.biomes = biomes;
        }

        private WorldConfig() {
            this(null, new ArrayList<>());
        }

        void initialize(Config config) {
            cycleLengthTicks = cycleLength.ticks();
            for (var biomeConfig : biomes) {
                if (biomeConfig.biomes.isEmpty()) {
                    if (defaultBiome == null)
                        defaultBiome = biomeConfig;
                    else
                        throw new IllegalArgumentException("Cannot have more than 1 default biome config");
                }
            }

            for (var biomeConfig : biomes) {
                try {
                    biomeConfig.initialize(this, config);
                } catch (Exception e) {
                    throw new RuntimeException("Could not initialize biome config", e);
                }
            }

            for (var biome : Biome.values())
                biomeConfig(biome.getKey());
        }

        public long time(World world, long offset) {
            return (world.getGameTime() + 6000 + offset) % cycleLengthTicks;
        }

        public long time(World world) {
            return time(world, 0);
        }

        public Optional<BiomeConfig> biomeConfig(Key key) {
            //noinspection PatternValidation
            return mappedBiomes.computeIfAbsent(Key.key(key.namespace(), key.value()), k -> {
                for (var biomeConfig : biomes) {
                    if (biomeConfig.biomes.contains(k))
                        return Optional.of(biomeConfig);
                }
                return defaultBiome == null ? Optional.empty() : Optional.of(defaultBiome);
            });
        }
    }

    @ConfigSerializable
    public static final class BiomeConfig {
        public final List<Key> biomes;
        public final @Required List<String> seasons;
        public transient final List<Season> mappedSeasons = new ArrayList<>();
        public transient final Object2LongMap<Season> durations = new Object2LongArrayMap<>();
        public transient int lastSeasonIndex = 0;

        public BiomeConfig(List<Key> biomes, List<String> seasons) {
            this.biomes = biomes;
            this.seasons = seasons;
        }

        private BiomeConfig() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        void initialize(WorldConfig worldConfig, Config config) {
            int totalWeight = 0;
            for (var seasonName : seasons) {
                Season season = config.seasons.get(seasonName);
                if (season == null)
                    throw new IllegalArgumentException("Invalid season name `" + seasonName + "`");
                mappedSeasons.add(season);
                totalWeight += season.weight;
            }

            for (var season : mappedSeasons) {
                long time = (long) (((double) season.weight / totalWeight) * worldConfig.cycleLengthTicks);
                durations.put(season, time);
            }
        }

        public record CurrentSeason(Season season, int index) {}

        public Optional<CurrentSeason> season(long time) {
            if (seasons.size() == 0)
                return Optional.empty();
            if (seasons.size() == 1)
                return Optional.of(new CurrentSeason(mappedSeasons.get(0), 0));

            long current = 0;
            int i = 0;
            for (var entry : durations.object2LongEntrySet()) {
                current += entry.getLongValue();
                if (current > time)
                    return Optional.of(new CurrentSeason(entry.getKey(), i));
                ++i;
            }
            return Optional.empty();
        }
    }

    private boolean ready;
    private final Map<Season, Int2IntMap> biomeMappings = new HashMap<>();

    public Seasons(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        ready = false;
        this.config = config.get(Config.class);
        this.config.initialize();
        ready = true;
    }

    @Override
    public void enable() {
        BiomeInjector biomeInjector = plugin.biomeInjector();

        if (config.biomeUpdateInterval != null) {
            plugin.scheduler().run(Task.repeating(ctx -> {
                Set<World> worldsToUpdate = new HashSet<>();
                for (var world : Bukkit.getWorlds()) {
                    boolean[] added = new boolean[]{false};
                    config.worlds.get(world).ifPresent(worldConfig -> {
                        for (var biomeConfig : worldConfig.biomes) {
                            if (added[0])
                                break;
                            biomeConfig.season(worldConfig.time(world)).ifPresent(currentSeason -> {
                                if (currentSeason.index == biomeConfig.lastSeasonIndex)
                                    return;
                                biomeConfig.lastSeasonIndex = currentSeason.index;
                                worldsToUpdate.add(world);
                                added[0] = true;
                            });
                        }
                    });
                }

                for (var world : worldsToUpdate) {
                    for (var player : world.getPlayers()) {
                        for (var chunkKey : MinecommonsPlugin.instance().trackedChunks().tracked(player)) {
                            plugin.biomeInjector().resendBiomes(world.getChunkAt(chunkKey), player);
                        }
                    }
                }
            }, config.biomeUpdateInterval.ms()));
        }

        var allExisting = new ArrayList<>(biomeInjector.byKey().values());
        for (var season : config.seasons.values()) {
            Int2IntMap mappings = new Int2IntArrayMap();
            biomeMappings.put(season, mappings);
            for (var existing : allExisting) {
                try {
                    PaperBiomeData data = existing.biomeData();
                    PaperBiomeEffects effects = data.effects();
                    double temp = data.temperature();
                    double humid = data.humidity();

                    if (season.foliageColor != null)
                        effects = effects.foliage(season.foliageColor.combineOn(
                                effects.foliage().orElseGet(() -> Vector3.rgb(plugin.foliageColors().get(temp, humid)))));
                    if (season.grassColor != null)
                        effects = effects.grass(season.grassColor.combineOn(
                                effects.grass().orElseGet(() -> Vector3.rgb(plugin.grassColors().get(temp, humid)))));
                    if (season.fogColor != null)
                        effects = effects.fog(season.fogColor.combineOn(effects.fog()));
                    if (season.skyColor != null)
                        effects = effects.sky(season.skyColor.combineOn(effects.sky()));
                    if (season.waterColor != null)
                        effects = effects.water(season.waterColor.combineOn(effects.water()));
                    if (season.waterFogColor != null)
                        effects = effects.waterFog(season.waterFogColor.combineOn(effects.waterFog()));
                    if (season.precipitation != null) {
                        data = data.precipitation(season.precipitation);
                        switch (season.precipitation) {
                            case SNOW -> data = data.temperature(0.1f);
                            case RAIN -> data = data.temperature(0.2f);
                        }
                    }

                    Key key = existing.key();
                    key = new NamespacedKey(plugin, key.namespace() + "_" + key.value() + "_" +
                            (config.obfuscateBiomeKeys ? RandomStringUtils.random(8, true, true) : season.name));
                    if (biomeInjector.get(key) != null) {
                        plugin.log(Logging.Level.WARNING, "Cannot inject season biome %s as it already exists", key.toString());
                        continue;
                    }
                    mappings.put(existing.id(), biomeInjector.inject(key, data.effects(effects)).id());
                } catch (RuntimeException e) {
                    plugin.log(Logging.Level.WARNING, e, "Could not make custom biome for %s of %s", existing.key(), season.name);
                }
            }
        }
    }

    @Override
    public void disable() {}

    @Override
    public void mapChunk(PacketEvent event) {
        if (!ready)
            return;
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();

        World world = player.getWorld();
        config.worlds.get(world).ifPresent(worldConfig -> {
            long time = worldConfig.time(world);
            int[] biomes = packet.getIntegerArrays().read(0);
            for (int i = 0; i < biomes.length; i++) {
                int j = i;
                int id = biomes[i];
                Key biomeKey = plugin.biomeInjector().get(id).key();
                worldConfig.biomeConfig(biomeKey).flatMap(biomeConfig -> biomeConfig.season(time)).ifPresent(season -> {
                    biomes[j] = biomeMappings.getOrDefault(season.season, emptyInt2IntMap).getOrDefault(id, id);
                });
            }
        });
    }
}
