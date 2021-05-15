package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.util.NaturaProtocol;
import com.gitlab.aecsocket.unifiedframework.core.scheduler.Task;
import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.HashMap;
import java.util.Map;

public class Weather implements Feature {
    public static final String ID = "weather";

    public interface Type {
        double downfall();
        boolean fog();
    }

    private final NaturaPlugin plugin;
    private final Map<Player, Double> currentDownfall = new HashMap<>();

    public Weather(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    @Override public String id() { return ID; }

    public Type weather(Block block) {
        Climate climate = plugin.climate();
        double temperature = climate.temperature(block);
        double humidity = climate.humidity(block);
        return new Type() {
            @Override
            public double downfall() {
                double minHumidity = 0.5 + (temperature * 0.2);
                if (humidity > minHumidity)
                    return (humidity-minHumidity) / (1-minHumidity);
                return 0;
            }

            @Override public boolean fog() { return temperature < 0.4 && humidity > 0.9; }
        };
    }

    @Override
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            if (!world.isClearWeather())
                world.setStorm(false);
        }
        plugin.scheduler().run(Task.repeating(ctx -> {
            currentDownfall.entrySet().removeIf(e -> !e.getKey().isValid());
            for (Player player : Bukkit.getOnlinePlayers()) {
                Type weather = weather(player.getLocation().getBlock());
                BossBar bar = plugin.bossBar(player);
                if (weather.fog() && !bar.hasFlag(BossBar.Flag.CREATE_WORLD_FOG)) {
                    bar.addFlag(BossBar.Flag.CREATE_WORLD_FOG);
                    // TODO wait until paper fixes
                    player.hideBossBar(bar);
                    player.showBossBar(bar);
                } else if (!weather.fog() && bar.hasFlag(BossBar.Flag.CREATE_WORLD_FOG)) {
                    bar.removeFlag(BossBar.Flag.CREATE_WORLD_FOG);
                    player.hideBossBar(bar);
                    player.showBossBar(bar);
                }

                double target = weather.downfall();
                double downfall = currentDownfall.computeIfAbsent(player, p -> target);
                downfall += (target - downfall) * 0.01;
                currentDownfall.put(player, downfall);
                NaturaProtocol.downfall(plugin, player, downfall);
            }
        }, Utils.MSPT));
    }

    @Override
    public void weatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) // if being set to rain, cancel
            event.setCancelled(true);
    }
}
