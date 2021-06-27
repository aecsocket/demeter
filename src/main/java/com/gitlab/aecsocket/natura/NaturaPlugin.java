package com.gitlab.aecsocket.natura;

import com.gitlab.aecsocket.minecommons.core.Logging;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import com.gitlab.aecsocket.minecommons.paper.plugin.BasePlugin;
import com.gitlab.aecsocket.minecommons.paper.scheduler.PaperScheduler;
import com.gitlab.aecsocket.natura.feature.Feature;
import com.gitlab.aecsocket.natura.feature.TimeDilation;
import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.util.Arrays;
import java.util.List;

public class NaturaPlugin extends BasePlugin<NaturaPlugin> {
    public static final int BSTATS_ID = 10976;

    private final PaperScheduler scheduler = new PaperScheduler(this);
    private final TimeDilation timeDilation = new TimeDilation(this);
    private final List<Feature<?>> features = Arrays.asList(timeDilation);

    @Override
    public void onEnable() {
        super.onEnable();
        Bukkit.getPluginManager().registerEvents(new NaturaListener(this), this);
    }

    @Override
    public void onDisable() {
        for (Feature<?> feature : features) {
            feature.disable();
        }
    }

    public @NotNull PaperScheduler scheduler() { return scheduler; }
    public TimeDilation timeDilation() { return timeDilation; }
    public @NotNull List<Feature<?>> features() { return features; }

    @Override
    protected void configOptionsDefaults(TypeSerializerCollection.Builder serializers, ObjectMapper.Factory.Builder mapperFactory) {
        super.configOptionsDefaults(serializers, mapperFactory);
        serializers.register(TimeDilation.Factor.class, new TimeDilation.Factor.Serializer());
    }

    @Override
    public boolean load() {
        if (super.load()) {
            if (setting(true, ConfigurationNode::getBoolean, "enable_bstats")) {
                Metrics metrics = new Metrics(this, BSTATS_ID);
            }

            for (Feature<?> feature : features) {
                String id = feature.id();
                try {
                    feature.load();
                    feature.enable();
                } catch (ConfigurateException e) {
                    log(Logging.Level.ERROR, e, "Could not load config for feature [%s]", id);
                    continue;
                }
                log(Logging.Level.VERBOSE, "Loaded feature [%s]", id);
            }
            return true;
        }
        return false;
    }

    @Override
    protected BaseCommand<NaturaPlugin> createCommand() throws Exception {
        return new NaturaCommand(this);
    }
}
