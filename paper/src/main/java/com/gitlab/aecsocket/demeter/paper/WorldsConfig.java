package com.gitlab.aecsocket.demeter.paper;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@ConfigSerializable
public final class WorldsConfig<C> implements Iterable<Map.Entry<String, C>> {
    public static final String DEFAULT = "default";

    private final @Setting(nodeFromParent = true) Map<String, C> handle = new HashMap<>();

    public Map<String, C> handle() { return handle; }

    public Optional<C> get(String key) {
        return Optional.ofNullable(handle.getOrDefault(key, handle.get(key)));
    }

    public Optional<C> get(World key) { return get(key.getName()); }

    @NotNull
    @Override
    public Iterator<Map.Entry<String, C>> iterator() {
        return handle.entrySet().iterator();
    }
}
