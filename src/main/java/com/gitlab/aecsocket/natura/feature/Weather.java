package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.unifiedframework.core.scheduler.Scheduler;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2D;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.*;

public class Weather implements Feature {
    public static final String ID = "weather";
    public static final Type TYPE = (config, state) -> {
        Weather feature = new Weather(
                config.get(Config.class),
                state.get(State.class)
        );
        feature.config.init();
        return feature;
    };

    @ConfigSerializable
    public static class Config {
        @Required public long updateInterval;
        @Required public WorldsConfig<WorldConfig> worlds;

        private void init() {
            worlds.init();
        }
    }

    @ConfigSerializable
    public static class WorldConfig {
        public boolean enabled = true;
        @Required public long forecastUpTo;
    }

    @ConfigSerializable
    public static class State {
        public Map<World, WorldState> worlds;
    }

    @ConfigSerializable
    public static class WorldState {
        public WindInfo wind;
        public List<WindInfo> windForecast;
    }

    public static class Info {
        public long until;
    }

    @ConfigSerializable
    public static class WindInfo extends Info {
        public Vector2D wind;
    }

    private Config config;
    private State state;
    private long nextUpdate = -1;

    public Weather(Config config, State state) {
        this.config = config;
        this.state = state;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public State state() { return state; }

    public WorldState state(World world) { return state.worlds.computeIfAbsent(world, __ -> new WorldState()); }

    @Override
    public void tasks(Scheduler scheduler) {
        scheduler.run(Task.repeating(ctx -> {
            boolean update = false;
            if (nextUpdate == -1)
                nextUpdate = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                WorldConfig config = this.config.worlds.get(world).orElse(null);
                if (config == null)
                    continue;
                WorldState state = state(world);


            }
        }, Utils.MSPT));
    }
}
