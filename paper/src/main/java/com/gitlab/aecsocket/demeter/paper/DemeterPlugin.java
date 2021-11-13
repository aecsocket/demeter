package com.gitlab.aecsocket.demeter.paper;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.demeter.paper.feature.*;
import com.gitlab.aecsocket.demeter.paper.util.GrassColors;
import com.gitlab.aecsocket.demeter.paper.util.ImageColors;
import com.gitlab.aecsocket.minecommons.core.ChatPosition;
import com.gitlab.aecsocket.minecommons.core.CollectionBuilder;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.core.Logging;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.serializers.ByKeySerializer;
import com.gitlab.aecsocket.minecommons.paper.biome.BiomeInjector;
import com.gitlab.aecsocket.minecommons.paper.plugin.BasePlugin;
import com.gitlab.aecsocket.minecommons.paper.scheduler.PaperScheduler;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemeterPlugin extends BasePlugin<DemeterPlugin> {
    public static final int BSTATS_ID = 13021;
    public static final String PATH_STATE = "state.conf";
    public static final String PATH_FOLIAGE = "foliage.png";
    public static final String PATH_GRASS = "grass.png";
    public static final String PERMISSION_PREFIX = "demeter";

    private final BiMap<String, ChatPosition> chatPositions = HashBiMap.create(CollectionBuilder.map(new HashMap<>(ChatPosition.VALUES))
            .put("boss_bar", (viewer, content) -> {
                if (viewer instanceof Player player)
                    bossBar(player).name(content);
            })
            .get()
    );
    /*
      * -> display
      seasons ->
        -> time_dilation
        -> climate ->
          -> phenomena
          -> temperature
          -> fertility
     */
    private final PaperScheduler scheduler = new PaperScheduler(this);
    private final Map<Player, BossBar> bossBars = new HashMap<>();
    private BiomeInjector biomeInjector;
    private ImageColors foliageColors;
    private GrassColors grassColors;
    private boolean allowSaving;

    private final Display display = new Display(this);
    private final TimeDilation timeDilation = new TimeDilation(this);
    private final Seasons seasons = new Seasons(this);
    private final Climate climate = new Climate(this);
    private final Fertility fertility = new Fertility(this);
    private final List<Feature<?>> features = Arrays.asList(
            display, timeDilation, seasons, climate, fertility
    );

    public PaperScheduler scheduler() { return scheduler; }
    public Map<Player, BossBar> bossBars() { return bossBars; }
    public BiomeInjector biomeInjector() { return biomeInjector; }
    public ImageColors foliageColors() { return foliageColors; }
    public GrassColors grassColors() { return grassColors; }

    public Display display() { return display; }
    public TimeDilation timeDilation() { return timeDilation; }
    public Seasons seasons() { return seasons; }
    public Climate climate() { return climate; }
    public Fertility fertility() { return fertility; }
    public List<Feature<?>> features() { return features; }

    public BossBar bossBar(Player player) {
        return bossBars.computeIfAbsent(player, p -> {
            BossBar bar = BossBar.bossBar(Component.empty(),
                    setting(0f, ConfigurationNode::getFloat, "boss_bar", "progress"),
                    setting(BossBar.Color.WHITE, (n, d) -> n.get(BossBar.Color.class, d), "boss_bar", "color"),
                    setting(BossBar.Overlay.PROGRESS, (n, d) -> n.get(BossBar.Overlay.class, d), "boss_bar", "overlay"));
            p.showBossBar(bar);
            return bar;
        });
    }

    public void removeBossBars() {
        for (var entry : bossBars.entrySet()) {
            entry.getKey().hideBossBar(entry.getValue());
        }
        bossBars.clear();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        Bukkit.getPluginManager().registerEvents(new DemeterListener(this), this);

        try {
            biomeInjector = new BiomeInjector();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log(Logging.Level.ERROR, e, "Could not set up biome injector");
        }

        protocol.manager().addPacketListener(new PacketAdapter(this, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketType type = event.getPacketType();
                if (type == PacketType.Play.Server.MAP_CHUNK)
                    features.forEach(f -> f.mapChunk(event));
            }
        });
    }

    @Override
    public void onDisable() {
        super.onDisable();
        removeBossBars();
        if (biomeInjector != null) {
            biomeInjector.uninjectAll();
        }
        save();
    }

    @Override
    protected void configOptionsDefaults(TypeSerializerCollection.Builder serializers, ObjectMapper.Factory.Builder mapperFactory) {
        super.configOptionsDefaults(serializers, mapperFactory);
        serializers
                .registerExact(ChatPosition.class, new ByKeySerializer<>(chatPositions))
                .registerExact(TimeDilation.Factor.class, new TimeDilation.Factor.Serializer())
                .registerExact(TimeDilation.CycleDuration.class, new TimeDilation.CycleDuration.Serializer())
                .registerExact(Seasons.ColorModifier.class, new Seasons.ColorModifier.Serializer());
    }

    public void save() {
        if (!allowSaving)
            return;
        var loader = loader(file(PATH_STATE));
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException e) {
            root = loader.createNode();
        }
        for (var feature : features) {
            ConfigurationNode featureRoot = root.node(feature.id());
            try {
                feature.save(featureRoot);
            } catch (SerializationException e) {
                log(Logging.Level.ERROR, e, "Could not save state for feature %s", feature.id());
            }
        }

        try {
            loader.save(root);
        } catch (ConfigurateException e) {
            log(Logging.Level.ERROR, e, "Could not save state");
        }
    }

    @Override
    public void load() {
        super.load();
        scheduler.cancel();
        biomeInjector.uninjectAll();
        features.forEach(Feature::disable);

        try {
            foliageColors = ImageColors.load(ImageIO.read(file(PATH_FOLIAGE)));
        } catch (IOException e) {
            log(Logging.Level.WARNING, e, "Could not load foliage colors from %s", PATH_FOLIAGE);
        }
        try {
            grassColors = new GrassColors(ImageColors.load(ImageIO.read(file(PATH_GRASS))));
        } catch (IOException e) {
            log(Logging.Level.WARNING, e, "Could not load grass colors from %s", PATH_GRASS);
        }

        int enabled = 0;
        for (var feature : features) {
            String id = feature.id();
            ConfigurationNode config = settings.root().node(id);
            if (!config.virtual()) {
                try {
                    feature.configure(config);
                } catch (Exception e) {
                    log(Logging.Level.WARNING, e, "Could not configure feature '%s'", id);
                }
                try {
                    feature.enable();
                    ++enabled;
                } catch (Exception e) {
                    log(Logging.Level.WARNING, e, "Could not enable feature '%s'", id);
                }
                log(Logging.Level.VERBOSE, "Enabled feature %s", id);
            }
        }
        log(Logging.Level.INFO, "Enabled %d feature(s)", enabled);

        if (file(PATH_STATE).exists()) {
            allowSaving = false;
            var loader = loader(file(PATH_STATE));
            try {
                ConfigurationNode node = loader.load();
                for (var feature : features) {
                    try {
                        feature.load(node.node(feature.id()));
                    } catch (SerializationException e) {
                        log(Logging.Level.ERROR, e, "Could not load state for feature %s", feature.id());
                    }
                }
                allowSaving = true;
            } catch (ConfigurateException e) {
                log(Logging.Level.ERROR, "Could not load state");
            }
        } else {
            allowSaving = true;
        }

        long autosaveInterval = setting(Duration.duration(1000 * 30), (n, d) -> n.get(Duration.class, d), "autosave_interval").ms();
        if (autosaveInterval > 0) {
            scheduler.run(Task.repeating(ctx -> {
                if (allowSaving)
                    save();
            }, autosaveInterval));
        }
    }

    @Override
    public void reload() {
        removeBossBars();
        save();
        super.reload();
    }

    @Override
    protected DemeterCommand createCommand() throws Exception {
        return new DemeterCommand(this);
    }
}
