package com.gitlab.aecsocket.natura.util;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@ConfigSerializable
public final class WorldConfigManager<C> implements Iterable<C> {
    public static final String DEFAULT = "default";

    @Setting(nodeFromParent = true)
    private final Map<String, C> worlds;
    private transient final Map<World, Optional<C>> cache = new HashMap<>();

    public WorldConfigManager(Map<String, C> worlds) {
        this.worlds = worlds;
    }

    public WorldConfigManager() { this(new HashMap<>()); }

    public Map<String, C> worlds() { return worlds; }

    public Optional<C> config(World world) {
        if (cache.containsKey(world))
            return cache.get(world);
        String name = world.getName();
        Optional<C> result = Optional.ofNullable(
                worlds.containsKey(name) ? worlds.get(name) : worlds.get(DEFAULT)
        );
        cache.put(world, result);
        return result;
    }

    @NotNull @Override public Iterator<C> iterator() { return worlds.values().iterator(); }
    @Override public void forEach(Consumer<? super C> action) { worlds.values().forEach(action); }
}
