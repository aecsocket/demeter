package com.github.aecsocket.demeter.paper.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.SerializationException;

import static com.github.aecsocket.demeter.paper.DemeterPlugin.PERMISSION_PREFIX;

import java.util.*;
import java.util.function.Supplier;

import com.github.aecsocket.demeter.paper.DemeterPlugin;
import com.github.aecsocket.demeter.paper.Feature;
import com.github.aecsocket.minecommons.core.ChatPosition;
import com.github.aecsocket.minecommons.core.expressions.math.MathNode;
import com.github.aecsocket.minecommons.core.expressions.node.EvaluationException;
import com.github.aecsocket.minecommons.core.i18n.I18N;
import com.github.aecsocket.minecommons.core.scheduler.Task;

public class Display extends Feature<Display.Config> {
    private static final Component disabled = Component.text("DISABLED PLACEHOLDER", NamedTextColor.RED);
    public static final String ID = "display";
    public static final String DISPLAY_POSITION = "display.position";
    public static final String DISPLAY_TIME = "display.time";
    public static final String DISPLAY_SEASON_NAME = "display.season.name";
    public static final String DISPLAY_SEASON_NONE = "display.season.none";
    public static final String DISPLAY_TEMPERATURE = "display.temperature";
    public static final String DISPLAY_HUMIDITY = "display.humidity";

    public enum Placeholder {
        TIME,
        SEASON,
        TEMPERATURE,
        HUMIDITY
    }

    // Config

    @ConfigSerializable
    public static final class Config {
        transient Display feature;
        public final long updateInterval;
        public final @Required Map<ChatPosition, PositionConfig> positions;
        public final @Required String temperatureFormat;
        public final @Required String humidityFormat;
        public final @Nullable MathNode temperatureExpr;
        public final @Nullable MathNode humidityExpr;

        private Config() {
            updateInterval = 1;
            positions = new HashMap<>();
            temperatureFormat = "";
            humidityFormat = "";
            temperatureExpr = null;
            humidityExpr = null;
        }

        void initialize(Display feature) {
            this.feature = feature;
        }
    }

    @ConfigSerializable
    public static final class PositionConfig {
        public final @Required String i18nKey;
        public final Set<Placeholder> placeholders;

        private PositionConfig() {
            i18nKey = null;
            placeholders = new HashSet<>();
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

    private Component ph(Map<Placeholder, Component> placeholders, Set<Placeholder> enabled, Placeholder key, Supplier<Component> factory) {
        return enabled.contains(key)
                ? placeholders.computeIfAbsent(key, f -> factory.get())
                : disabled;
    }

    private double expr(@Nullable MathNode expr, double value) {
        try {
            return expr == null ? value : expr.set("x", value).eval();
        } catch (EvaluationException e) {
            return value;
        }
    }

    private String sign(double val) {
        return val > 0 ? "positive"
                : val < 0 ? "negative"
                : "zero";
    }

    @Override
    public void enable() {
        if (config == null)
            return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(PERMISSION_PREFIX + ".feature.display"))
                    continue;
                I18N i18n = plugin.i18n();
                Locale locale = player.locale();
                World world = player.getWorld();

                TimeDilation.DayStage dayStage = plugin.timeDilation().dayStage(world);
                double dayRaw = world.getFullTime() % 24_000 / 24_000d;
                double dayProgress = dayStage == TimeDilation.DayStage.NONE ? dayRaw
                        : plugin.timeDilation().config().worlds.get(world)
                            .map(cfg -> cfg.dayProgress(world))
                            .orElse(dayRaw);
                double hours = ((dayProgress + 0.25) % 1) * 24;

                Climate.State climate = plugin.climate().state(player.getLocation().getBlock());

                Map<Placeholder, Component> phCache = new HashMap<>();
                for (var entry : config.positions.entrySet()) {
                    ChatPosition position = entry.getKey();
                    PositionConfig posConfig = entry.getValue();
                    Set<Placeholder> phs = posConfig.placeholders;
                    position.send(player, i18n.line(locale, DISPLAY_POSITION + "." + posConfig.i18nKey,
                            c -> c.of("time", ph(phCache, phs, Placeholder.TIME,
                                    () -> i18n.line(locale, DISPLAY_TIME + "." + plugin.timeDilation().dayStage(world).key(),
                                            d -> d.format("hour", "%02d", (int) (hours)),
                                            d -> d.format("minute", "%02d", (int) (hours % 1 * 60))))),
                            c -> c.of("season", ph(phCache, phs, Placeholder.SEASON,
                                    () -> plugin.seasons().config().worlds.get(world)
                                            .flatMap(cfg -> cfg.biomeConfig(player.getLocation().getBlock().getBiome().getKey()))
                                            .flatMap(cfg -> cfg.season(world))
                                            .map(s -> i18n.line(locale, DISPLAY_SEASON_NAME + "." + s.season().name()))
                                            .orElse(i18n.line(locale, DISPLAY_SEASON_NONE)))),
                            c -> c.of("temperature", ph(phCache, phs, Placeholder.TEMPERATURE,
                                    () -> {
                                double temperature = expr(config.temperatureExpr, climate.temperature());
                                return i18n.line(locale, DISPLAY_TEMPERATURE + "." + sign(temperature),
                                                d -> d.format("value", config.temperatureFormat, temperature));
                                    })),
                            c -> c.of("humidity", ph(phCache, phs, Placeholder.HUMIDITY,
                                    () -> i18n.line(locale, DISPLAY_HUMIDITY,
                                            d -> d.format("value", config.humidityFormat, expr(config.humidityExpr, climate.humidity())))))));
                }
            }
        }, config.updateInterval));
    }

    @Override
    public void disable() {}
}
