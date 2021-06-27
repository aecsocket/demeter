package com.gitlab.aecsocket.natura.util;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.*;

@ConfigSerializable
public class WorldConfigs<C> implements Iterable<C> {
    public static final String DEFAULT = "default";

    private @Setting(nodeFromParent = true) final Map<String, C> config;
    private transient final Map<UUID, C> resolved = new HashMap<>();

    public WorldConfigs(Map<String, C> config) {
        this.config = config;
    }

    public WorldConfigs() { this(new HashMap<>()); }

    public Map<String, C> config() { return config; }
    public Map<UUID, C> resolved() { return resolved; }

    public Optional<C> config(World world) {
        return Optional.ofNullable(resolved.computeIfAbsent(world.getUID(),
                u -> config.getOrDefault(world.getName(), config.get(DEFAULT))));
    }

    @Override public @NotNull Iterator<C> iterator() { return resolved.values().iterator(); }
}
