package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.HashMap;
import java.util.Map;

public class GlobalTemperature implements Tickable {
    @ConfigSerializable
    public static class Settings {
        public double multiplier = 0.9;
        public long shiftInterval = 1000;
    }

    private final NaturaPlugin plugin;
    private final Map<Player, Double> currentTemperature = new HashMap<>();
    private Settings settings;
    private long lastTemperatureShift;

    public GlobalTemperature(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }
    public Map<Player, Double> currentTemperature() { return currentTemperature; }
    public double currentTemperature(Player player) { return currentTemperature.computeIfAbsent(player, __ -> 0d); }

    public Settings settings() { return settings; }
    public void settings(Settings settings) { this.settings = settings; }

    @Override
    public void tick(TickContext tickContext) {
        long time = System.currentTimeMillis();
        if (time - lastTemperatureShift >= settings.shiftInterval) {
            lastTemperatureShift = time;
            currentTemperature.entrySet().removeIf(entry -> !entry.getKey().isOnline());

            for (WorldData data : plugin.worlds().values()) {
                Temperature temperature = data.temperature();
                if (!temperature.settings().enabled)
                    continue;

                for (Player player : data.world().getPlayers()) {
                    double current = currentTemperature(player);
                    double target = temperature.targetTemperature(player);
                    current += (target - current) * settings.multiplier;

                    player.sendActionBar(Component.text(String.format("%.2f / %.2f", current, target)));
                    currentTemperature.put(player, current);
                }
            }
        }
    }
}
