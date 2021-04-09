package com.gitlab.aecsocket.natura;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.WirePacket;
import com.gitlab.aecsocket.natura.feature.GlobalTemperature;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import com.gitlab.aecsocket.unifiedframework.core.registry.Identifiable;
import com.gitlab.aecsocket.unifiedframework.core.serialization.configurate.vector.Vector3ISerializer;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector3I;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.BasePlugin;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.ServerLoadEvent;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.util.NamingSchemes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NaturaPlugin extends BasePlugin<Identifiable> implements Tickable {
    public static final int BSTATS_PLUGIN_ID = 10976;
    private static NaturaPlugin instance;
    public static NaturaPlugin instance() { return instance; }

    private final Map<World, WorldData> worlds = new HashMap<>();
    private final GlobalTemperature temperature = new GlobalTemperature(this);
    private PaperCommandManager commandManager;
    private ProtocolManager protocol;
    private World dummyWorld;

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.getCommandContexts().registerContext(World.class, ctx -> {
            World world = Bukkit.getWorld(ctx.popFirstArg());
            if (world == null)
                throw new InvalidCommandArgument("Invalid world");
            return world;
        });
        commandManager.registerCommand(new NaturaCommand(this));

        protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new NaturaPacketAdapter(this));

        Bukkit.getPluginManager().registerEvents(new NaturaListener(this), this);

        schedulerLoop.register(this);

        dummyWorld = Bukkit.createWorld(new WorldCreator("dummy"));
    }

    protected void createConfigOptions() {
        configOptions = configOptions.serializers(builder -> {
            ObjectMapper.Factory mapper = ObjectMapper.factoryBuilder()
                    .defaultNamingScheme(NamingSchemes.SNAKE_CASE)
                    .build();
            builder
                    .register(Vector3I.class, Vector3ISerializer.INSTANCE)
                    .registerAnnotatedObjects(mapper);
        });
    }

    @Override
    @EventHandler(priority = EventPriority.MONITOR)
    public void serverLoad(ServerLoadEvent event) {
        createConfigOptions();
        super.serverLoad(event);
    }

    @Override
    protected void loadSettings(List<LoggingEntry> result) {
        super.loadSettings(result);
        if (settings.root() != null) {
            if (setting(n -> n.getBoolean(true), "enable_bstats")) {
                Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
                // TODO add some cool charts
            }
            temperature.settings(setting(n -> n.get(GlobalTemperature.Settings.class), "temperature"));
        }
    }

    @Override
    public boolean load(List<LoggingEntry> result) {
        boolean success = super.load(result);

        for (var entry : setting(n -> n.childrenMap().entrySet(), "worlds")) {
            String name = entry.getKey().toString();
            World world = Bukkit.getWorld(name);
            if (world == null) {
                result.add(LoggingEntry.of(LogLevel.WARN, "World '%s' is not loaded", name));
                continue;
            }
            try {
                worlds.put(world, new WorldData(this, world, entry.getValue().get(WorldData.Settings.class)));
            } catch (SerializationException e) {
                result.add(LoggingEntry.of(LogLevel.WARN, e, "Could not load info for world '%s'", name));
                continue;
            }
            result.add(LoggingEntry.of(LogLevel.VERBOSE, "Loaded info for world '%s'", name));
        }

        return success;
    }

    @Override protected Map<Path, Type> registryTypes() { return Collections.emptyMap(); }
    @Override protected void registerDefaults() {}

    public PaperCommandManager commandManager() { return commandManager; }
    public Map<World, WorldData> worlds() { return worlds; }
    public WorldData worldData(World world) { return worlds.get(world); }
    public ProtocolManager protocol() { return protocol; }
    public World dummyWorld() { return dummyWorld; }

    @Override
    public void tick(TickContext tickContext) {
        for (WorldData world : worlds.values()) {
            tickContext.tick(world);
        }
        tickContext.tick(temperature);
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
}
