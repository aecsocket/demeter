package com.gitlab.aecsocket.demeter.paper.feature;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.demeter.paper.WorldsConfig;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.core.Logging;
import com.gitlab.aecsocket.minecommons.core.Ticks;
import com.gitlab.aecsocket.minecommons.core.biome.Precipitation;
import com.gitlab.aecsocket.minecommons.core.i18n.I18N;
import com.gitlab.aecsocket.minecommons.core.i18n.Renderable;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.serializers.Serializers;
import com.gitlab.aecsocket.minecommons.core.vector.cartesian.Vector3;
import com.gitlab.aecsocket.minecommons.paper.MinecommonsPlugin;
import com.gitlab.aecsocket.minecommons.paper.biome.BiomeInjector;
import com.gitlab.aecsocket.minecommons.paper.biome.PaperBiomeData;
import com.gitlab.aecsocket.minecommons.paper.biome.PaperBiomeEffects;
import io.leangen.geantyref.TypeToken;
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

    // Utils

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
    ) implements Renderable {
        public static final String SEASON = "season";

        @Override
        public Component render(I18N i18n, Locale locale) {
            return i18n.line(locale, SEASON + "." + name);
        }
    }

    // Config

    @ConfigSerializable
    public static final class Config {
        transient Seasons feature;
        public final boolean obfuscateBiomeKeys;
        public final @Nullable Duration chunkUpdateInterval;
        public final @Required Map<String, Season> seasons;
        public final @Required WorldsConfig<WorldConfig> worlds;

        private Config() {
            obfuscateBiomeKeys = false;
            chunkUpdateInterval = Duration.duration(1000 * 60);
            seasons = new HashMap<>();
            worlds = new WorldsConfig<>();
        }

        void initialize(Seasons feature) {
            this.feature = feature;
            for (var entry : worlds) {
                try {
                    entry.getValue().initialize(this);
                } catch (Exception e) {
                    throw new RuntimeException("Could not initialize config for world `" + entry.getKey() + "`", e);
                }
            }
        }
    }

    @ConfigSerializable
    public static final class WorldConfig {
        transient Config config;
        public final @Required Duration cycleLength;
        public final @Required List<BiomeConfig> biomes;
        public transient @Nullable BiomeConfig defaultBiome;
        public transient final Map<Key, Optional<BiomeConfig>> mappedBiomes = new HashMap<>();

        private WorldConfig() {
            cycleLength = Duration.duration(0);
            biomes = new ArrayList<>();
        }

        void initialize(Config config) {
            this.config = config;
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
                    biomeConfig.initialize(this);
                } catch (Exception e) {
                    throw new RuntimeException("Could not initialize biome config", e);
                }
            }

            for (var biome : Biome.values())
                biomeConfig(biome.getKey());
        }

        public Optional<BiomeConfig> defaultBiome() { return Optional.ofNullable(defaultBiome); }

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
        transient WorldConfig worldConfig;
        public final List<Key> biomes;
        public final @Required List<String> seasons;
        public transient final List<Season> mappedSeasons = new ArrayList<>();
        public transient final Object2LongMap<Season> durations = new Object2LongArrayMap<>();
        public transient int lastSeasonIndex = 0;

        private BiomeConfig() {
            biomes = new ArrayList<>();
            seasons = new ArrayList<>();
        }

        void initialize(WorldConfig worldConfig) {
            this.worldConfig = worldConfig;
            Config config = worldConfig.config;
            int totalWeight = 0;
            for (var seasonName : seasons) {
                Season season = config.seasons.get(seasonName);
                if (season == null)
                    throw new IllegalArgumentException("Invalid season name `" + seasonName + "`");
                mappedSeasons.add(season);
                totalWeight += season.weight;
            }

            for (var season : mappedSeasons) {
                long time = (long) (((double) season.weight / totalWeight) * worldConfig.cycleLength.ms());
                durations.put(season, time);
            }
        }

        public long startsAt(Season season) {
            long time = 0;
            for (var current : mappedSeasons) {
                if (current.equals(season))
                    return time;
                time += durations.getLong(current);
            }
            return -1;
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

        public Optional<CurrentSeason> season(World world) {
            return season(worldConfig.config.feature.time(world));
        }
    }

    private boolean ready;
    private final Map<Season, Int2IntMap> biomeMappings = new HashMap<>();
    private final Object2LongMap<UUID> seasonTime = new Object2LongArrayMap<>();

    public Seasons(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    public Map<Season, Int2IntMap> biomeMappings() { return biomeMappings; }
    public Object2LongMap<UUID> seasonTime() { return seasonTime; }

    public long time(UUID worldId) { return seasonTime.getOrDefault(worldId, 0); }
    public long time(World world) { return time(world.getUID()); }

    public void time(World world, long time) {
        seasonTime.put(world.getUID(), time);
        updateChunks(world);
    }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        ready = false;
        this.config = config.get(Config.class);
        if (this.config == null)
            throw new SerializationException("null");
        this.config.initialize(this);
        ready = true;
    }

    @Override
    public void enable() {
        BiomeInjector biomeInjector = plugin.biomeInjector();

        plugin.scheduler().run(Task.repeating(ctx -> {
            for (var world : Bukkit.getWorlds()) {
                config.worlds.get(world).ifPresent(worldConfig -> {
                    long time = (time(world) + ctx.delta()) % worldConfig.cycleLength.ms();
                    seasonTime.put(world.getUID(), time);
                });
            }
        }, Ticks.MSPT));

        if (config.chunkUpdateInterval != null) {
            plugin.scheduler().run(Task.repeating(ctx -> updateChunks(), config.chunkUpdateInterval.ms()));
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

    public void forceUpdateChunks(World world) {
        for (var player : world.getPlayers()) {
            for (var chunkKey : MinecommonsPlugin.instance().trackedChunks().tracked(player)) {
                plugin.biomeInjector().resendBiomes(world.getChunkAt(chunkKey), player);
            }
        }
    }

    public Optional<Season> standardSeason(World world) {
        return config.worlds.get(world)
                .flatMap(WorldConfig::defaultBiome)
                .flatMap(cfg -> cfg.season(world))
                .map(ssn -> ssn.season);
    }

    public void updateChunks(World world) {
        boolean[] update = new boolean[]{false};
        config.worlds.get(world).ifPresent(worldConfig -> {
            for (var biomeConfig : worldConfig.biomes) {
                if (update[0])
                    break;
                biomeConfig.season(world).ifPresent(currentSeason -> {
                    if (currentSeason.index == biomeConfig.lastSeasonIndex)
                        return;
                    biomeConfig.lastSeasonIndex = currentSeason.index;
                    update[0] = true;
                });
            }
        });

        if (update[0])
            forceUpdateChunks(world);
    }

    public void updateChunks() {
        for (var world : Bukkit.getWorlds()) {
            updateChunks(world);
        }
    }

    @Override
    public void disable() {}

    @Override
    public void save(ConfigurationNode node) throws SerializationException {
        node.node("season_time").set(seasonTime);
    }

    @Override
    public void load(ConfigurationNode node) throws SerializationException {
        var seasonTime = node.node("season_time").get(new TypeToken<Map<UUID, Long>>(){}, Collections.emptyMap());
        this.seasonTime.putAll(seasonTime);
    }

    @Override
    public void mapChunk(PacketEvent event) {
        if (!ready)
            return;
        Player player = event.getPlayer();
        PacketContainer packet = event.getPacket();

        World world = player.getWorld();
        config.worlds.get(world).ifPresent(worldConfig -> {
            long time = time(world);
            int[] biomes = packet.getIntegerArrays().read(0);
            for (int i = 0; i < biomes.length; i++) {
                int j = i;
                int id = biomes[i];
                Key biomeKey = plugin.biomeInjector().get(id).key();
                worldConfig.biomeConfig(biomeKey).flatMap(biomeConfig -> biomeConfig.season(time)).ifPresent(season ->
                        biomes[j] = biomeMappings.getOrDefault(season.season, emptyInt2IntMap).getOrDefault(id, id));
            }
        });
    }
}
