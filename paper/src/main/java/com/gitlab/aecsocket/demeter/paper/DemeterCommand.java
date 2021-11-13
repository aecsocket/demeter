package com.gitlab.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.context.CommandContext;
import com.gitlab.aecsocket.demeter.paper.feature.Seasons;
import com.gitlab.aecsocket.demeter.paper.feature.TimeDilation;
import com.gitlab.aecsocket.demeter.paper.util.SeasonArgument;
import com.gitlab.aecsocket.demeter.paper.util.Timeline;
import com.gitlab.aecsocket.minecommons.core.Duration;
import com.gitlab.aecsocket.minecommons.paper.command.DurationArgument;
import com.gitlab.aecsocket.minecommons.paper.command.KeyArgument;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gitlab.aecsocket.demeter.paper.DemeterPlugin.PERMISSION_PREFIX;

/* package */ class DemeterCommand extends BaseCommand<DemeterPlugin> {
    private static final double msPerMin = 1000 * 60;

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
                .permission(PERMISSION_PREFIX + "%s.command.seasons.time.set")
                .handler(c -> handle(c, this::seasonsTimeSet)));
    }

    private Component worldConfigName(Locale locale, String key) {
        return lc.safe(locale, PREFIX_COMMAND + ".config.world." +
                (WorldsConfig.DEFAULT.equals(key) ? "default" : "normal"),
                "key", key);
    }

    private <C> C config(Feature<C> feature) {
        C config = feature.config;
        if (config == null)
            throw error("no_config",
                    "feature", "time_dilation");
        return config;
    }

    private void timeDilationStatus(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        TimeDilation timeDilation = plugin.timeDilation();
        var config = config(timeDilation);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        TimeDilation.DayStage dayStage = timeDilation.dayStage(world);
        Component dayStageLc = lc.safe(locale, "day_stage." + dayStage.key());
        timeDilation.config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            send(sender, locale, "time_dilation.status.info",
                    "world", world.getName(),
                    "stage", dayStageLc,
                    "duration", worldConfig.appliedDuration(world).get(dayStage).asDuration().asString(locale),
                    "total", worldConfig.appliedDuration(world).asString(locale),
                    "default_total", worldConfig.duration.asString(locale));
        }, () -> {
            send(sender, locale, "time_dilation.status.no_info",
                    "world", world.getName(),
                    "stage", dayStageLc);
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
                    send(sender, locale, "seasons.get",
                            "world", world.getName(),
                            "biome", biome.toString(),
                            "season", season.season().name(lc, locale));
                }, () -> {
                    throw error("no_season");
                });
            }, () -> {
                throw error("no_biome_config",
                        "biome", biome.toString());
            });
        }, () -> {
            throw error("no_world_config",
                    "world", world.getName());
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
                    throw error("no_season_config",
                            "season", season.name());

                long was = seasons.time(world);
                seasons.time(world, startsAt);
                send(sender, locale, "seasons.set",
                        "world", world.getName(),
                        "season", season.name(lc, locale),
                        "now", Duration.duration(startsAt).asString(locale),
                        "was", Duration.duration(was).asString(locale));
            }, () -> {
                throw error("no_biome_config",
                        "biome", biome.toString());
            });
        }, () -> {
            throw error("no_world_config",
                    "world", world.getName());
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
                send(sender, locale, "seasons.timeline.world",
                        "timeline", new Timeline(length)
                                .complete(progress)
                                .build(),
                        "world", target.getName(),
                        "elapsed", Duration.duration(elapsed).asString(locale),
                        "cycle", Duration.duration(cycle).asString(locale),
                        "percent", String.format(locale, "%.1f", progress * 100));

                for (var biomeConfig : worldConfig.biomes) {
                    Timeline timeline = new Timeline(length).complete(progress);
                    for (var season : biomeConfig.mappedSeasons) {
                        timeline.add(new Timeline.Section(TextColor.color(season.color().rgb()), (double) biomeConfig.durations.getLong(season) / cycle));
                    }
                    send(sender, locale, "seasons.timeline.entry",
                            "timeline", timeline.build(),
                            "season", biomeConfig.season(elapsed)
                                    .map(s -> s.season().name(lc, locale))
                                    .orElse(Component.empty()));
                }
            }, () -> {
                if (world != null)
                    throw error("no_world_config",
                            "world", world.getName());
            });
        }
    }

    private void seasonsTimeGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        long time = seasons.time(world);
        send(sender, locale, "seasons.time.get",
                "world", world.getName(),
                "time", Duration.duration(time).asString(locale));
    }

    private void seasonsTimeSet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        Duration time = ctx.get("time");
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        long was = seasons.time(world);
        seasons.time(world, time.ms());
        send(sender, locale, "seasons.time.set",
                "world", world.getName(),
                "now", time.asString(locale),
                "was", Duration.duration(was).asString(locale));
    }
}
