package com.github.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.bukkit.parsers.location.LocationArgument;
import cloud.commandframework.context.CommandContext;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import static com.github.aecsocket.demeter.paper.DemeterPlugin.PERMISSION_PREFIX;

import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.aecsocket.demeter.paper.feature.Climate;
import com.github.aecsocket.demeter.paper.feature.Fertility;
import com.github.aecsocket.demeter.paper.feature.Seasons;
import com.github.aecsocket.demeter.paper.feature.TimeDilation;
import com.github.aecsocket.demeter.paper.util.SeasonArgument;
import com.github.aecsocket.demeter.paper.util.Timeline;
import com.github.aecsocket.minecommons.core.Duration;
import com.github.aecsocket.minecommons.paper.command.DurationArgument;
import com.github.aecsocket.minecommons.paper.command.KeyArgument;
import com.github.aecsocket.minecommons.paper.plugin.BaseCommand;

/* package */ class DemeterCommand extends BaseCommand<DemeterPlugin> {
    public static final String ERROR_NO_CONFIG = "error.no_config";
    public static final String ERROR_NO_WORLD_CONFIG = "error.no_world_config";
    public static final String ERROR_NO_BIOME_CONFIG = "error.no_biome_config";
    public static final String ERROR_NO_SEASON_CONFIG = "error.no_season_config";
    public static final String ERROR_NO_SEASON = "error.no_season";

    public static final String BLOCK_LOCATION = "block_location";
    public static final String CLIMATE_STATE = "climate_state";
    public static final String DAY_STAGE = "day_stage";

    public static final String COMMAND_TIME_DILATION_STATUS_INFO = "command.time_dilation.status.info";
    public static final String COMMAND_TIME_DILATION_STATUS_NO_INFO = "command.time_dilation.status.no_info";
    public static final String COMMAND_SEASONS_GET = "command.seasons.get";
    public static final String COMMAND_SEASONS_SET = "command.seasons.set";
    public static final String COMMAND_SEASONS_TIMELINE_WORLD = "command.seasons.timeline.world";
    public static final String COMMAND_SEASONS_TIMELINE_ENTRY = "command.seasons.timeline.entry";
    public static final String COMMAND_SEASONS_TIME_GET = "command.seasons.time.get";
    public static final String COMMAND_SEASONS_TIME_SET = "command.seasons.time.set";
    public static final String COMMAND_CLIMATE_GET_TOTAL = "command.climate.get.total";
    public static final String COMMAND_CLIMATE_GET_FACTOR = "command.climate.get.factor";
    public static final String COMMAND_CLIMATE_GET_FACTOR_KEY = "command.climate.get.factor_key";
    public static final String COMMAND_FERTILITY_GET_TOTAL = "command.fertility.get.total";
    public static final String COMMAND_FERTILITY_GET_FACTOR = "command.fertility.get.factor";
    public static final String COMMAND_FERTILITY_GET_FACTOR_KEY = "command.fertility.get.factor_key";

    public DemeterCommand(DemeterPlugin plugin) throws Exception {
        super(plugin, "demeter",
                (mgr, root) -> mgr.commandBuilder(root, ArgumentDescription.of("Plugin main command."), "dem"));

        captions.registerMessageFactory(SeasonArgument.ARGUMENT_PARSE_FAILURE_SEASON, captionLocalizer);

        var timeDilation = root
                .literal("time-dilation", ArgumentDescription.of("Time dilation feature commands."));
        manager.command(timeDilation
                .literal("status", ArgumentDescription.of("Shows the status of time dilation in a world."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the status for."))
                .permission(PERMISSION_PREFIX + ".command.time-dilation.status")
                .handler(c -> handle(c, this::timeDilationStatus)));

        var seasons = root
                .literal("seasons", ArgumentDescription.of("Seasons feature commands."));
        manager.command(seasons
                .literal("get", ArgumentDescription.of("Gets the current season at a location."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the season for."))
                .argument(KeyArgument.<CommandSender>newBuilder("biome")
                                .withSuggestionsProvider((ctx, inp) -> Stream.of(Biome.values()).map(biome -> biome.getKey().toString()).collect(Collectors.toList()))
                                .asOptional(), ArgumentDescription.of("The biome to get the season for."))
                .permission(PERMISSION_PREFIX + ".command.seasons.get")
                .handler(c -> handle(c, this::seasonsGet)));
        manager.command(seasons
                .literal("set", ArgumentDescription.of("Sets the time of a world to correspond with a season."))
                .argument(SeasonArgument.of(plugin, "season"), ArgumentDescription.of("The season to set to."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to set the season for."))
                .argument(KeyArgument.<CommandSender>newBuilder("biome")
                        .withSuggestionsProvider((ctx, inp) -> Stream.of(Biome.values()).map(biome -> biome.getKey().toString()).collect(Collectors.toList()))
                        .asOptional(), ArgumentDescription.of("The biome to set the season for."))
                .permission(PERMISSION_PREFIX + ".command.seasons.set")
                .handler(c -> handle(c, this::seasonsSet)));
        manager.command(seasons
                .literal("timeline", ArgumentDescription.of("Shows a timeline of all seasons for worlds."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to show for."))
                .permission(PERMISSION_PREFIX + ".command.seasons.timeline")
                .handler(c -> handle(c, this::seasonsTimeline)));

        var seasonsTime = seasons
                .literal("time", ArgumentDescription.of("Directly manages the raw time values of worlds."));
        manager.command(seasonsTime
                .literal("get", ArgumentDescription.of("Gets the raw time value of a world, in milliseconds."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the time for."))
                .permission(PERMISSION_PREFIX + ".command.seasons.time.get")
                .handler(c -> handle(c, this::seasonsTimeGet)));
        manager.command(seasonsTime
                .literal("set", ArgumentDescription.of("Sets the raw time value of a world."))
                .argument(DurationArgument.of("time"), ArgumentDescription.of("The time to set to."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to set the time for."))
                .permission(PERMISSION_PREFIX + ".command.seasons.time.set")
                .handler(c -> handle(c, this::seasonsTimeSet)));

        var climate = root
                .literal("climate", ArgumentDescription.of("Climate feature commands."));
        manager.command(climate
                .literal("get", ArgumentDescription.of("Gets the climate at a specific block."))
                .argument(LocationArgument.optional("location"), ArgumentDescription.of("The block to get the climate at."))
                .permission(PERMISSION_PREFIX + ".command.climate.get")
                .handler(c -> handle(c, this::climateGet)));

        var fertility = root
                .literal("fertility", ArgumentDescription.of("Fertility feature commands."));
        manager.command(fertility
                .literal("get", ArgumentDescription.of("Gets how fertile a specific block is."))
                .argument(LocationArgument.optional("location"), ArgumentDescription.of("The block to get fertility for."))
                .permission(PERMISSION_PREFIX + ".command.fertility.get")
                .handler(c -> handle(c, this::fertilityGet)));
    }

    private Component fBlockLocation(Locale locale, Location location) {
        return i18n.line(locale, BLOCK_LOCATION,
                c -> c.format("x", "%,.0f", location.getX()),
                c -> c.format("y", "%,.0f", location.getY()),
                c -> c.format("z", "%,.0f", location.getZ()));
    }

    private Component fState(Locale locale, Climate.State state) {
        return i18n.line(locale, CLIMATE_STATE,
                c -> c.format("temperature", "%,.2f", state.temperature()),
                c -> c.format("humidity", "%,.1f", state.humidity() * 100));
    }

    private Component fDayStage(Locale locale, TimeDilation.DayStage stage) {
        return i18n.line(locale, DAY_STAGE + "." + stage.key());
    }

    private <C> C config(Feature<C> feature) {
        C config = feature.config;
        if (config == null)
            throw error(ERROR_NO_CONFIG,
                    c -> c.of("feature", feature.id()));
        return config;
    }

    private void timeDilationStatus(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        TimeDilation timeDilation = plugin.timeDilation();
        var config = config(timeDilation);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        TimeDilation.DayStage stage = timeDilation.dayStage(world);
        Component dayStageLc = fDayStage(locale, stage);
        timeDilation.config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            send(sender, locale, COMMAND_TIME_DILATION_STATUS_INFO,
                    c -> c.of("world", world.getName()),
                    c -> c.of("stage", dayStageLc),
                    c -> c.of("duration", worldConfig.appliedDuration(world).get(stage).asDuration().asString(locale)),
                    c -> c.of("total", worldConfig.appliedDuration(world).asString(locale)),
                    c -> c.of("default_total", worldConfig.duration.asString(locale)));
        }, () -> {
            send(sender, locale, COMMAND_TIME_DILATION_STATUS_NO_INFO,
                    c -> c.of("world", world.getName()),
                    c -> c.of("stage", dayStageLc));
        });
    }

    private void seasonsGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);
        Key biome = defaultedArg(ctx, "biome", pSender, p -> p.getLocation().getBlock().getBiome().getKey());

        config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            worldConfig.biomeConfig(biome).ifPresentOrElse(biomeConfig -> {
                biomeConfig.season(world).ifPresentOrElse(season -> {
                    send(sender, locale, COMMAND_SEASONS_GET,
                            c -> c.of("world", world.getName()),
                            c -> c.of("biome", biome),
                            c -> c.of("season", season.season()));
                }, () -> {
                    throw error(ERROR_NO_SEASON);
                });
            }, () -> {
                throw error(ERROR_NO_BIOME_CONFIG,
                        c -> c.of("biome", biome));
            });
        }, () -> {
            throw error(ERROR_NO_WORLD_CONFIG,
                    c -> c.of("world", world.getName()));
        });
    }

    private void seasonsSet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        Seasons.Season season = ctx.get("season");
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);
        Key biome = defaultedArg(ctx, "biome", pSender, p -> p.getLocation().getBlock().getBiome().getKey());

        seasons.config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            worldConfig.biomeConfig(biome).ifPresentOrElse(biomeConfig -> {
                long startsAt = biomeConfig.startsAt(season);
                if (startsAt == -1)
                    throw error(ERROR_NO_SEASON_CONFIG,
                            c -> c.of("season", season.name()));

                long was = seasons.time(world);
                seasons.time(world, startsAt);
                send(sender, locale, COMMAND_SEASONS_SET,
                        c -> c.of("world", world.getName()),
                        c -> c.of("season", season),
                        c -> c.of("now", Duration.duration(startsAt).asString(locale)),
                        c -> c.of("was", Duration.duration(was).asString(locale)));
            }, () -> {
                throw error(ERROR_NO_BIOME_CONFIG,
                        c -> c.of("biome", biome));
            });
        }, () -> {
            throw error(ERROR_NO_WORLD_CONFIG,
                    c -> c.of("world", world.getName()));
        });
    }

    private void seasonsTimeline(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        World world = ctx.getOrDefault("world", null);
        int length = 40;

        for (var target : world == null ? Bukkit.getWorlds() : Collections.singleton(world)) {
            config.worlds.get(target).ifPresentOrElse(worldConfig -> {
                long elapsed = seasons.time(target);
                long cycle = worldConfig.cycleLength.ms();
                double progress = (double) elapsed / cycle;
                send(sender, locale, COMMAND_SEASONS_TIMELINE_WORLD,
                        c -> c.of("timeline", new Timeline(length)
                                .complete(progress)
                                .build()),
                        c -> c.of("world", target.getName()),
                        c -> c.of("elapsed", Duration.duration(elapsed).asString(locale)),
                        c -> c.of("cycle", Duration.duration(cycle).asString(locale)),
                        c -> c.format("percent", "%,.1f", progress * 100));

                for (var biomeConfig : worldConfig.biomes) {
                    Timeline timeline = new Timeline(length).complete(progress);
                    for (var season : biomeConfig.mappedSeasons) {
                        timeline.add(new Timeline.Section(TextColor.color(season.color().rgb()), (double) biomeConfig.durations.getLong(season) / cycle));
                    }
                    send(sender, locale, COMMAND_SEASONS_TIMELINE_ENTRY,
                            c -> c.of("timeline", timeline.build()),
                            c -> c.of("season", biomeConfig.season(elapsed)
                                    .map(s -> s.season().render(i18n, locale))
                                    .orElse(Component.empty())));
                }
            }, () -> {
                if (world != null)
                    throw error(ERROR_NO_WORLD_CONFIG,
                            c -> c.of("world", world.getName()));
            });
        }
    }

    private void seasonsTimeGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        long time = seasons.time(world);
        send(sender, locale, COMMAND_SEASONS_TIME_GET,
                c -> c.of("world", world.getName()),
                c -> c.of("time", Duration.duration(time).asString(locale)));
    }

    private void seasonsTimeSet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        Duration time = ctx.get("time");
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        long was = seasons.time(world);
        seasons.time(world, time.ms());
        send(sender, locale, COMMAND_SEASONS_TIME_SET,
                c -> c.of("world", world.getName()),
                c -> c.of("now", time.asString(locale)),
                c -> c.of("was", Duration.duration(was).asString(locale)));
    }

    private void climateGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Climate climate = plugin.climate();
        config(climate);
        Location location = defaultedArg(ctx, "location", pSender, Entity::getLocation);
        Block block = location.getBlock();

        var factors = climate.stateFactors(block);
        Climate.State state = climate.state(factors);
        send(sender, locale, COMMAND_CLIMATE_GET_TOTAL,
                c -> c.of("location", fBlockLocation(locale, location)),
                c -> c.of("state", fState(locale, state)));
        for (int i = 0; i < factors.size(); i++) {
            int j = i;
            var factor = factors.get(i);
            send(sender, locale, COMMAND_CLIMATE_GET_FACTOR,
                    c -> c.of("index", j+1),
                    c -> c.line("key", COMMAND_CLIMATE_GET_FACTOR_KEY + "." + factor.key()),
                    c -> c.of("state", fState(locale, factor.value())));
        }
    }

    private void fertilityGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Fertility fertility = plugin.fertility();
        config(fertility);
        // if standing on farmland, will make it target the crop instead of the farmland
        Location location = defaultedArg(ctx, "location", pSender, Entity::getLocation).add(0, 0.1, 0);
        Block block = location.getBlock();

        var factors = fertility.growthChanceFactors(block);
        double value = fertility.growthChance(factors);
        send(sender, locale, COMMAND_FERTILITY_GET_TOTAL,
                c -> c.of("location", fBlockLocation(locale, location)),
                c -> c.format("fertility", "%,.1f", value * 100));
        for (int i = 0; i < factors.size(); i++) {
            int j = i;
            var factor = factors.get(i);
            send(sender, locale, COMMAND_FERTILITY_GET_FACTOR,
                    c -> c.of("index", j+1),
                    c -> c.of("key", COMMAND_FERTILITY_GET_FACTOR_KEY + "." + factor.key()),
                    c -> c.format("fertility", "%,.1f", factor.value() * 100));
        }
    }
}
