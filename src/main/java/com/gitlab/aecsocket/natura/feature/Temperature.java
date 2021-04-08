package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class Temperature implements Feature {
    @ConfigSerializable
    public static class Settings {
        public boolean enabled = true;
    }

    private final WorldData world;
    private final Settings settings;

    public Temperature(WorldData world, Settings settings) {
        this.world = world;
        this.settings = settings;
    }

    public WorldData world() { return world; }
    public Settings settings() { return settings; }

    @Override
    public void tick(TickContext tickContext) {
        if (!settings.enabled) return;
    }
}
