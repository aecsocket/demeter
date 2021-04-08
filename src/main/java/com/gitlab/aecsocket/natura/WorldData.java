package com.gitlab.aecsocket.natura;

import com.gitlab.aecsocket.natura.feature.Calendar;
import com.gitlab.aecsocket.natura.feature.Feature;
import com.gitlab.aecsocket.natura.feature.Temperature;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import org.bukkit.World;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public class WorldData implements Tickable {
    @ConfigSerializable
    public static class Settings {
        public final Calendar.Settings calendar = new Calendar.Settings();
        public final Temperature.Settings temperature = new Temperature.Settings();
    }

    private final NaturaPlugin plugin;
    private final World world;
    private final Settings settings;

    private final Calendar calendar;
    private final Temperature temperature;
    private final Feature[] features = new Feature[2];

    public WorldData(NaturaPlugin plugin, World world, Settings settings) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
        calendar = new Calendar(this, settings.calendar, new Calendar.State() /* todo */);
        temperature = new Temperature(this, settings.temperature);
        features[0] = calendar;
        features[1] = temperature;
    }

    public NaturaPlugin plugin() { return plugin; }
    public World world() { return world; }
    public Settings settings() { return settings; }

    public Calendar calendar() { return calendar; }
    public Temperature temperature() { return temperature; }

    public void blockGrow(BlockGrowEvent event) {
        for (Feature feature : features) {
            feature.blockGrow(event);
        }
    }

    @Override
    public void tick(TickContext tickContext) {
        for (Feature feature : features) {
            tickContext.tick(feature);
        }
    }
}
