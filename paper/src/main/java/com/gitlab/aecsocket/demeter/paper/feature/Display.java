package com.gitlab.aecsocket.demeter.paper.feature;

import com.gitlab.aecsocket.demeter.paper.DemeterPlugin;
import com.gitlab.aecsocket.demeter.paper.Feature;
import com.gitlab.aecsocket.minecommons.core.ChatPosition;
import com.gitlab.aecsocket.minecommons.core.scheduler.Task;
import com.gitlab.aecsocket.minecommons.core.translation.Localizer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Locale;

import static com.gitlab.aecsocket.demeter.paper.DemeterPlugin.PERMISSION_PREFIX;

public class Display extends Feature<Display.Config> {
    public static final String ID = "display";

    // Config

    @ConfigSerializable
    public static final class Config {
        transient Display feature;
        public final long updateInterval;
        public final @Required ChatPosition chatPosition;

        private Config() {
            updateInterval = 1;
            chatPosition = ChatPosition.ACTION_BAR;
        }

        void initialize(Display feature) {
            this.feature = feature;
        }
    }

    public Display(DemeterPlugin plugin) {
        super(plugin);
    }

    @Override public String id() { return ID; }

    @Override
    public void configure(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
        if (this.config == null)
            throw new SerializationException("null");
        this.config.initialize(this);
    }

    @Override
    public void enable() {
        if (config == null)
            return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(PERMISSION_PREFIX + ".feature.display"))
                    continue;
                Localizer lc = plugin.lc();
                Locale locale = player.locale();

                World world = player.getWorld();
                TimeDilation timeDilation = plugin.timeDilation();
                Seasons seasons = plugin.seasons();

                TimeDilation.DayStage dayStage = timeDilation.dayStage(world);
                double dayRaw = world.getFullTime() % 24_000 / 24_000d;
                double dayProgress = dayStage == TimeDilation.DayStage.NONE ? dayRaw
                        : timeDilation.config().worlds.get(world)
                            .map(cfg -> cfg.dayProgress(world))
                            .orElse(dayRaw);
                double hours = ((dayProgress + 0.25) % 1) * 24;

                lc.get(locale, "display.main",
                        "time", lc.safe(locale, "display.time." + timeDilation.dayStage(world).key(),
                                        "hour", String.format("%02d", (int) (hours)),
                                        "minute", String.format("%02d", (int) (hours % 1 * 60))),
                        "season", seasons.config().worlds.get(world)
                                        .flatMap(cfg -> cfg.biomeConfig(player.getLocation().getBlock().getBiome().getKey()))
                                        .flatMap(cfg -> cfg.season(world))
                                        .map(s -> lc.safe(locale, "display.season.name." + s.season().name()))
                                        .orElse(lc.safe(locale, "display.season.none"))
                        )
                        .ifPresent(c -> config.chatPosition.send(player, c));
            }
        }, config.updateInterval));
    }

    @Override
    public void disable() {}
}
