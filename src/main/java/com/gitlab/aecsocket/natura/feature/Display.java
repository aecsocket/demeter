package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.parsing.math.MathExpressionNode;
import com.gitlab.aecsocket.unifiedframework.core.parsing.math.MathParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.Locale;
import java.util.Optional;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

public class Display implements Feature {
    public static final String ID = "display";
    public static final Type TYPE = (config, state) -> {
        Display feature = new Display(
                config.get(Config.class)
        );
        feature.config.init();
        return feature;
    };

    @ConfigSerializable
    public static class Config {
        @Required public String temperatureFormat;
        @Required public String temperatureFunction;
        private transient MathExpressionNode temperatureFunctionNode;

        private void init() {
            temperatureFunctionNode = MathParser.parses(temperatureFunction);
        }
    }

    private Config config;

    public Display(Config config) {
        this.config = config;
    }

    @Override public String id() { return ID; }

    public Config config() { return config; }
    @Override public Object state() { return null; }

    @Override
    public void tick(TickContext tickContext) {
        Seasons seasons = plugin().feature(Seasons.ID);
        Temperature temperature = plugin().feature(Temperature.ID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Locale locale = player.locale();

            Component textSeason;
            if (seasons == null)
                textSeason = plugin().gen(locale, "display.season.disabled");
            else {
                Seasons.Season season = seasons.season(player);
                if (season == null)
                    textSeason = plugin().gen(locale, "display.season.no_season");
                else
                    textSeason = season.getLocalizedName(locale);
            }

            Component textTemperature;
            if (temperature == null)
                textTemperature = plugin().gen(locale, "display.temperature.disabled");
            else {
                double current = temperature.currentTemperature(player);
                config.temperatureFunctionNode.setVariable("x", current);
                textTemperature = plugin().gen(locale, "display.temperature.temperature",
                        "temperature", String.format(locale, config.temperatureFormat, config.temperatureFunctionNode.value()));
            }

<<<<<<< HEAD
            long time = (player.getWorld().getTime() + 6000) % 24000;
            player.sendActionBar(plugin().gen(locale, "display.action_bar",
                    "hours", String.format("%02d", time / 1000),
                    "minutes", String.format("%02d", (int) (((time % 1000) / 1000d) * 60)),
=======
            player.sendActionBar(plugin().gen(locale, "display.action_bar",
>>>>>>> 5391bb6e921b8ac49d252d075012c5f4a025fe08
                    "season", textSeason,
                    "temperature", textTemperature));
        }
    }
}
