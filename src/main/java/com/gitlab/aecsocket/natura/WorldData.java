package com.gitlab.aecsocket.natura;

import com.comphenix.protocol.events.PacketEvent;
import com.gitlab.aecsocket.natura.feature.Calendar;
import com.gitlab.aecsocket.natura.feature.Feature;
import com.gitlab.aecsocket.natura.feature.Temperature;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import com.gitlab.aecsocket.unifiedframework.core.loop.Tickable;
import org.bukkit.World;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
    private final List<Feature> features = new ArrayList<>();

    public WorldData(NaturaPlugin plugin, World world, Settings settings) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
        calendar = new Calendar(this, settings.calendar, new Calendar.State() /* todo */);
        temperature = new Temperature(this, settings.temperature);
        features.add(calendar);
        features.add(temperature);
    }

    public NaturaPlugin plugin() { return plugin; }
    public World world() { return world; }
    public Settings settings() { return settings; }

    public Calendar calendar() { return calendar; }
    public Temperature temperature() { return temperature; }

    private void run(Consumer<Feature> function) {
        for (Feature feature : features) {
            function.accept(feature);
        }
    }

    public void blockGrow(BlockGrowEvent event) { run(f -> f.blockGrow(event)); }
    public void mapChunk(PacketEvent event) { run(f -> f.mapChunk(event)); }
    public void itemConsume(PlayerItemConsumeEvent event) { run(f -> f.itemConsume(event)); }

    @Override
    public void tick(TickContext tickContext) {
        run(tickContext::tick);
    }
}
