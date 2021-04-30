package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.unifiedframework.core.scheduler.Scheduler;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class TimeDilation implements Feature {
    public static final String ID = "time_dilation";
    public static final Type TYPE = (config, state) -> {
        TimeDilation feature = new TimeDilation(
                config.get(Config.class)
        );
        feature.config.init();
        return feature;
    };

    @ConfigSerializable
    public static class Config {
        public WorldsConfig<WorldConfig> worlds;

        private void init() {
            worlds.init();
        }
    }

    @ConfigSerializable
    public static class WorldConfig {
        public boolean enabled;
    }

    private Config config;
    private double tickProgress;

    public TimeDilation(Config config) {
        this.config = config;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public Object state() { return null; }

    @Override
    public void setUp(Scheduler scheduler) {
        scheduler.run(Task.repeating(ctx -> {
            if (plugin().dayLengthMultiplier() == 1)
                return;
            for (World world : Bukkit.getWorlds()) {
                WorldConfig data = config.worlds.get(world).orElse(null);
                if (data == null || !data.enabled)
                    continue;
                if (world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE) == Boolean.FALSE)
                    continue;

                long time = world.getFullTime() - 1;
                double timeDilation = 1 / plugin().dayLengthMultiplier();
                tickProgress += timeDilation;
                while (tickProgress > 1) {
                    --tickProgress;
                    ++time;
                }
                world.setFullTime(time);
            }
        }, Utils.MSPT));
    }
}
