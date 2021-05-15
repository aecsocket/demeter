package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.WorldConfigManager;
import com.gitlab.aecsocket.unifiedframework.core.parsing.math.MathExpressionNode;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Cardinal;
import com.gitlab.aecsocket.unifiedframework.core.util.vector.Vector2D;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.Locale;

public class Display implements Feature {
    public static final String ID = "display";

    public enum Format {
        ACTION_BAR,
        BOSS_BAR
    }

    @ConfigSerializable
    public static final class Config {
        public boolean enabled;
        public long updateInterval = 1000;
        public Format format;
        public WorldConfigManager<WorldConfig> worlds;

        public String temperatureFormat = "%.2f";
        public MathExpressionNode temperatureExpression;

        public String humidityFormat = "%.2f";
        public MathExpressionNode humidityExpression;

        public String bodyTemperatureFormat = "%.2f";
        public MathExpressionNode bodyTemperatureExpression;
        public boolean calculateTarget;

        public String windSpeedFormat = "%.1f";
    }

    @ConfigSerializable
    public static final class WorldConfig {
        private static final WorldConfig empty = new WorldConfig();

        public boolean enabled;
    }

    private final NaturaPlugin plugin;
    private Config config;

    public Display(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Config config() { return config; }

    @Override public String id() { return ID; }

    @Override
    public void acceptConfig(ConfigurationNode config) throws SerializationException {
        this.config = config.get(Config.class);
    }

    private String value(double value, Locale locale, String format, MathExpressionNode expr) {
        return String.format(locale, format, expr == null ? value : expr.set("v", value).value());
    }

    @Override
    public void start() {
        if (!config.enabled) return;
        plugin.scheduler().run(Task.repeating(ctx -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                World world = player.getWorld();
                if (!player.hasPermission("natura.feature.display")) continue;
                if (!config.worlds.config(world).orElse(WorldConfig.empty).enabled) continue;

                Block block = player.getLocation().getBlock();
                Climate climate = plugin.climate();
                Seasons seasons = plugin.seasons();
                Seasons.Season season = seasons.season(world, block.getBiome());
                BodyTemperature bodyTemp = plugin.bodyTemperature();
                Vector2D wind = climate.state(world).wind;

                Locale locale = player.locale();
                long time = (world.getTime() + 6000) % NaturaPlugin.TICKS_PER_DAY;
                Component text = plugin.gen(locale, "display.text",
                        "hours", String.format("%02d", (int) (time / NaturaPlugin.HOUR) /* % 24 */),
                        "minutes", String.format("%02d", (int) ((time / NaturaPlugin.MINUTE) % 60)),
                        "seconds", String.format("%02d", (int) ((time / NaturaPlugin.SECOND) % 60)),
                        "temperature", value(climate.temperature(block), locale, config.temperatureFormat, config.temperatureExpression),
                        "humidity", value(climate.humidity(block), locale, config.humidityFormat, config.humidityExpression),
                        "season", season == null ? plugin.gen(locale, "display.season.none") : plugin.gen(locale, "display.season.season",
                                "season", season.localizedName(plugin, locale)),
                        "current_body_temperature", value(bodyTemp.current(player), locale, config.bodyTemperatureFormat, config.bodyTemperatureExpression),
                        "target_body_temperature", config.calculateTarget ? value(bodyTemp.target(player), locale, config.bodyTemperatureFormat, config.bodyTemperatureExpression) : "",
                        "wind_direction", plugin.gen(locale, "display.cardinal." + Cardinal.Secondary.closest(wind.bearing(Climate.NORTH)).name().toLowerCase(Locale.ROOT)),
                        "wind_speed", value(wind.length(), locale, config.windSpeedFormat, null)); // wind vector is always m/s
                switch (config.format) {
                    case ACTION_BAR:
                        player.sendActionBar(text);
                        break;
                    case BOSS_BAR:
                        plugin.bossBar(player).name(text);
                        break;
                }
            }
        }, config.updateInterval));
    }
}
