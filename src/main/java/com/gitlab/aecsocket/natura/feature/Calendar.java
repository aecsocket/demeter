package com.gitlab.aecsocket.natura.feature;

import com.gitlab.aecsocket.natura.WorldData;
import com.gitlab.aecsocket.unifiedframework.core.loop.TickContext;
import org.bukkit.event.block.BlockGrowEvent;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Required;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Calendar implements Feature {
    public static final int TICKS_PER_DAY = 20 * 60 * 20;

    @ConfigSerializable
    public static class Settings {
        public boolean enabled = true;
        public final List<Season> seasons = new ArrayList<>();

        @Override
        public String toString() {
            return "Settings{" +
                    "enabled=" + enabled +
                    ", seasons=" + seasons +
                    '}';
        }
    }

    @ConfigSerializable
    public static class Season {
        @Required
        public final String name = null;
        @Required
        public double duration = 1;
        public double fertility = 1;

        @Override
        public String toString() {
            return "Season{" +
                    "name='" + name + '\'' +
                    ", duration=" + duration +
                    ", fertility=" + fertility +
                    '}';
        }
    }

    @ConfigSerializable
    public static class State {
        public int seasonTicks;
        public int seasonIndex;
    }

    private final WorldData world;
    private final Settings settings;
    private final State state;
    private Season season;

    public Calendar(WorldData world, Settings settings, State state) {
        this.world = world;
        this.settings = settings;
        this.state = state;
        updateSeason();
    }

    public void updateSeason() {
        season = settings.seasons.get(state.seasonIndex);
    }

    public WorldData world() { return world; }
    public Settings settings() { return settings; }
    public State state() { return state; }
    public Season season() { return season; }
    public Season seasonInfo() { return season; }

    @Override
    public void blockGrow(BlockGrowEvent event) {
        if (!world.world().equals(event.getBlock().getWorld()))
            return;
        if (ThreadLocalRandom.current().nextDouble() > season.fertility)
            event.setCancelled(true);
    }

    @Override
    public void tick(TickContext tickContext) {
        if (!settings.enabled) return;
        ++state.seasonTicks;
        if (state.seasonTicks > (season.duration * TICKS_PER_DAY)) {
            ++state.seasonIndex;
            state.seasonTicks = 0;
            if (state.seasonIndex >= settings.seasons.size()) {
                state.seasonIndex = 0;
            }
            updateSeason();
        }
    }
}
