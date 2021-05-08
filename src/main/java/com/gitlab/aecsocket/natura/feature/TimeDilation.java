package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.TimeUtils;
import com.gitlab.aecsocket.natura.util.WorldConfigManager;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.HashMap;
import java.util.Map;

public class TimeDilation implements Feature {
    public static final String ID = "time_dilation";

    @ConfigSerializable
    public static class Config {
        public boolean enabled;
        public WorldConfigManager<WorldConfig> worlds;
    }

    @ConfigSerializable
    public static class WorldConfig {
        private static final WorldConfig empty = new WorldConfig();

        public boolean enabled;
        public String factor;
        public transient double durationFactor;
    }

    private final NaturaPlugin plugin;
    private Config config;
    private final Map<World, Double> progress = new HashMap<>();

    public TimeDilation(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        for (WorldConfig cfg : this.config.worlds.worlds().values()) {
            cfg.durationFactor = 1 / TimeUtils.timeMultiplier(cfg.factor);
        }
    }

    @Override
    public void start() {
        if (!config.enabled) return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (World world : Bukkit.getWorlds()) {
                if (!world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)) continue;
                WorldConfig cfg = config.worlds.config(world).orElse(WorldConfig.empty);
                if (!cfg.enabled) continue;

                double progress = this.progress.getOrDefault(world, 0d);
                progress += cfg.durationFactor;
                long time = world.getFullTime() - 1;
                while (progress >= 1) {
                    --progress;
                    ++time;
                }
                world.setFullTime(time);
                this.progress.put(world, progress);
            }
        }, Utils.MSPT));
    }
}
