package com.gitlab.aecsocket.natura;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import com.gitlab.aecsocket.unifiedframework.core.registry.Identifiable;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.BasePlugin;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerLoadEvent;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class NaturaPlugin extends BasePlugin<Identifiable> implements Tickable {
    public static final int BSTATS_PLUGIN_ID = 10976;
    private static NaturaPlugin instance;
    public static NaturaPlugin instance() { return instance; }

    private final Map<World, WorldData> worlds = new HashMap<>();
    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        commandManager = new PaperCommandManager(this);
        commandManager.getCommandContexts().registerContext(World.class, ctx -> {
            World world = Bukkit.getWorld(ctx.popFirstArg());
            if (world == null)
                throw new InvalidCommandArgument("Invalid world");
            return world;
        });
        commandManager.getCommandCompletions().registerCompletion("worlds", ctx ->
                Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
        commandManager.registerCommand(new NaturaCommand(this));

        Bukkit.getPluginManager().registerEvents(new NaturaListener(this), this);

        schedulerLoop.register(this);
    }

    @Override
    protected void loadSettings(List<LoggingEntry> result) {
        super.loadSettings(result);
        if (settings.root() != null) {
            if (setting(n -> n.getBoolean(true), "enable_bstats")) {
                Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
                // TODO add some cool charts
            }
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

    @Override
    public void tick(TickContext tickContext) {
        for (WorldData world : worlds.values()) {
            tickContext.tick(world);
        }
    }
}
