package com.gitlab.aecsocket.demeter.paper;

import com.gitlab.aecsocket.demeter.paper.feature.Seasons;
import com.gitlab.aecsocket.demeter.paper.feature.TimeDilation;
import com.gitlab.aecsocket.minecommons.core.Logging;
import com.gitlab.aecsocket.minecommons.core.scheduler.Scheduler;
import com.gitlab.aecsocket.minecommons.paper.plugin.BasePlugin;
import com.gitlab.aecsocket.minecommons.paper.scheduler.PaperScheduler;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.util.Arrays;
import java.util.List;

public class DemeterPlugin extends BasePlugin<DemeterPlugin> {
    public static final int BSTATS_ID = 13021;

    private final PaperScheduler scheduler = new PaperScheduler(this);
    private final TimeDilation timeDilation = new TimeDilation(this);
    private final Seasons seasons = new Seasons(this);
    private final List<Feature<?>> features = Arrays.asList(
            timeDilation, seasons
    );

    public PaperScheduler scheduler() { return scheduler; }
    public TimeDilation timeDilation() { return timeDilation; }
    public Seasons seasons() { return seasons; }
    public List<Feature<?>> features() { return features; }

    @Override
    protected void configOptionsDefaults(TypeSerializerCollection.Builder serializers, ObjectMapper.Factory.Builder mapperFactory) {
        super.configOptionsDefaults(serializers, mapperFactory);
        serializers
                .registerExact(TimeDilation.Factor.class, new TimeDilation.Factor.Serializer());
    }

    @Override
    public void load() {
        super.load();
        scheduler.cancel();
        int enabled = 0;
        for (var feature : features) {
            String id = feature.id();
            ConfigurationNode config = settings.root().node(id);
            if (!config.virtual()) {
                try {
                    feature.configure(config);
                } catch (SerializationException e) {
                    log(Logging.Level.WARNING, e, "Could not configure feature '%s'", id);
                }
                try {
                    feature.enable();
                    ++enabled;
                } catch (Exception e) {
                    log(Logging.Level.WARNING, e, "Could not enable feature '%s'", id);
                }
                log(Logging.Level.VERBOSE, "Enabled feature %s", id);
            }
        }
        log(Logging.Level.INFO, "Enabled %d feature(s)", enabled);
    }

    @Override
    protected DemeterCommand createCommand() throws Exception {
        return new DemeterCommand(this);
    }
}
