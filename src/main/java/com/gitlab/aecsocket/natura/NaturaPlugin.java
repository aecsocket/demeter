package com.gitlab.aecsocket.natura;

import co.aikar.commands.PaperCommandManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.WirePacket;
import com.gitlab.aecsocket.natura.feature.*;
import com.gitlab.aecsocket.unifiedframework.core.util.MapInit;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.BasePlugin;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.PluginHook;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerLoadEvent;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;

public final class NaturaPlugin extends BasePlugin {
    public static final int BSTATS_PLUGIN_ID = 10976;
    public static final String STATE_ROOT = "state";
    public static final int TICKS_PER_DAY = 20 * 60 * 20;
    public static final List<String> DEFAULT_RESOURCES = Arrays.asList(
            SETTINGS_FILE,
            LANGUAGE_ROOT + "/default_en-US.conf",
            LANGUAGE_ROOT + "/en-US.conf",
            Seasons.PATH_FOLIAGE_COLORS
    );
    private static NaturaPlugin instance;
    public static NaturaPlugin plugin() { return instance; }

    private final Map<String, Feature.Type> featureTypes = Collections.unmodifiableMap(MapInit.of(new HashMap<String, Feature.Type>())
            .init(Display.ID, Display.TYPE)
            .init(TimeDilation.ID, TimeDilation.TYPE)
            .init(Seasons.ID, Seasons.TYPE)
            .init(Temperature.ID, Temperature.TYPE)
            /*.init("weather", Weather.FACTORY)
            .init("wind", Wind.FACTORY)*/
            .get());
    private final Map<String, Feature> features = new HashMap<>();
    private PaperCommandManager commandManager;
    private ProtocolManager protocol;

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        commandManager = new PaperCommandManager(this);
        NaturaCommand command = new NaturaCommand();
        command.initManager(commandManager);
        commandManager.registerCommand(command);

        protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new NaturaPacketAdapter());

        Bukkit.getPluginManager().registerEvents(new NaturaListener(), this);
    }

    @Override
    public void onDisable() {
        features.values().forEach(Feature::onDisable);
        save();
    }

    @Override protected List<String> defaultResources() { return DEFAULT_RESOURCES; }
    // TODO maybe support some deeper API?
    @Override protected List<PluginHook> hooks() { return Collections.emptyList(); }

    @Override
    @EventHandler
    public boolean serverLoad(ServerLoadEvent event) {
        if (!super.serverLoad(event))
            return false;
        int saveInterval = setting(n -> (int) (n.getDouble(30) * 20 * 60), "save_interval");
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::save, saveInterval, saveInterval);
        features.values().forEach(f -> f.serverLoad(event));
        return true;
    }

    @Override
    public boolean load(List<LoggingEntry> result) {
        if (super.load(result)) {
            if (setting(n -> n.getBoolean(true), "enable_bstats")) {
                Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
                // TODO add some cool charts
            }

            for (var entry : setting(n -> n.childrenMap().entrySet(), "features")) {
                // Get config
                String id = entry.getKey().toString();
                Feature.Type type = featureTypes.get(id);
                if (type == null) {
                    result.add(LoggingEntry.of(LogLevel.WARN, "Invalid feature type '%s'", id));
                    continue;
                }

                ConfigurationNode state = loadState(id);
                Feature feature;
                try {
                    feature = type.load(entry.getValue(), state);
                } catch (SerializationException | RuntimeException e) {
                    result.add(LoggingEntry.of(LogLevel.WARN, e, "Could not load '%s'", id));
                    continue;
                }

                if (feature == null) {
                    result.add(LoggingEntry.of(LogLevel.WARN, "Feature type '%s' created null feature", id));
                    continue;
                }


                features.put(id, feature);
                result.add(LoggingEntry.of(LogLevel.VERBOSE, "Loaded feature '%s'", id));
            }
            return true;
        }
        return false;
    }

    public void save() {
        for (Feature feature : features.values()) {
            String id = feature.id();
            try {
                saveState(id, BasicConfigurationNode.root(configOptions).set(feature.state()));
            } catch (SerializationException e) {
                log(LogLevel.WARN, e, "Could not create state for '%s'", id);
            }
        }
        log(LogLevel.VERBOSE, "Saved state");
    }

    public Map<String, Feature.Type> featureTypes() { return featureTypes; }

    public Map<String, Feature> features() { return new HashMap<>(features); }
    @SuppressWarnings("unchecked")
    public <T extends Feature> T feature(String id) { return (T) features.get(id); }
    public void registerFeature(Feature feature) { features.put(feature.id(), feature); }
    public void unregisterFeature(String id) { features.remove(id); }

    public PaperCommandManager commandManager() { return commandManager; }
    public ProtocolManager protocol() { return protocol; }

    public File stateFolder() { return new File(getDataFolder(), STATE_ROOT); }
    public File stateFile(String id) { return new File(stateFolder(), id + ".conf"); }
    public HoconConfigurationLoader stateLoader(String id) {
        return HoconConfigurationLoader.builder()
                .file(stateFile(id))
                .defaultOptions(configOptions)
                .build();
    }
    public ConfigurationNode loadState(String id) {
        File stateFile = stateFile(id);
        if (!stateFile.exists()) {
            return BasicConfigurationNode.root(configOptions);
        }
        try {
            return stateLoader(id).load();
        } catch (ConfigurateException e) {
            log(LogLevel.WARN, e, "Could not load state for '%s'", id);
            return null;
        }
    }
    public void saveState(String id, ConfigurationNode state) {
        try {
            stateLoader(id).save(state);
        } catch (ConfigurateException e) {
            log(LogLevel.WARN, e, "Could not save state for '%s'", id);
        }
    }

    public void sendPacket(PacketContainer packet, Player target, boolean wire) {
        try {
            if (wire) {
                WirePacket wirePacket = WirePacket.fromPacket(packet);
                protocol.sendWirePacket(target, wirePacket);
            } else
                protocol.sendServerPacket(target, packet);
        } catch (InvocationTargetException | RuntimeException e) {
            log(LogLevel.WARN, e, "Could not send packet to %s (%s)", target.getName(), target.getUniqueId());
        }
    }

    public void sendPacket(PacketContainer packet, Player target) {
        sendPacket(packet, target, false);
    }

    public void sendPacket(Player target, PacketType type, boolean wire, Consumer<PacketContainer> builder) {
        PacketContainer packet = new PacketContainer(type);
        builder.accept(packet);
        sendPacket(packet, target, wire);
    }

    public void sendPacket(Player target, PacketType type, Consumer<PacketContainer> builder) {
        sendPacket(target, type, false, builder);
    }

    public double dayLengthMultiplier() { return setting(n -> n.getDouble(1), "day_length_multiplier"); }
    public long ticksPerDay() { return (long) (TICKS_PER_DAY * dayLengthMultiplier()); }
}
