package com.gitlab.aecsocket.natura.feature;

import org.bukkit.World;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ConfigSerializable
public class WorldsConfig<T> {
    public static final String DEFAULT_WORLD = "default";

    @Setting(value = "world", nodeFromParent = true)
    private Map<String, T> rawWorlds;
    private transient Map<World, T> worlds = new HashMap<>();

    public Map<String, T> rawWorlds() { return rawWorlds; }
    public Map<World, T> worlds() { return worlds; }

    public void init() {
        if (rawWorlds.containsKey(DEFAULT_WORLD)) {
            worlds.put(null, rawWorlds.get(DEFAULT_WORLD));
        }
    }

    public Optional<T> get(World world) {
        if (worlds.containsKey(world))
            return Optional.of(worlds.get(world));
        String name = world.getName();
        if (rawWorlds.containsKey(name)) {
            T config = rawWorlds.get(name);
            worlds.put(world, config);
            return Optional.of(config);
        }
        return Optional.ofNullable(worlds.get(null));
    }
}
