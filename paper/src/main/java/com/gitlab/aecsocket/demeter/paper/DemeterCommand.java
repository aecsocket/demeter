package com.gitlab.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.standard.LongArgument;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.context.CommandContext;
import com.gitlab.aecsocket.demeter.paper.feature.Seasons;
import com.gitlab.aecsocket.demeter.paper.util.KeyArgument;
import com.gitlab.aecsocket.demeter.paper.util.SeasonArgument;
import com.gitlab.aecsocket.demeter.paper.util.Timeline;
import com.gitlab.aecsocket.minecommons.core.Duration;
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

/* package */ class DemeterCommand extends BaseCommand<DemeterPlugin> {
    private static final double msPerMin = 1000 * 60;

    public DemeterCommand(DemeterPlugin plugin) throws Exception {
        super(plugin, "demeter",
                (mgr, root) -> mgr.commandBuilder(root, ArgumentDescription.of("Plugin main command."), "dem"));

        var timeDilation = root
                .literal("time-dilation", ArgumentDescription.of("Time dilation feature commands."));
        manager.command(timeDilation
                .literal("day-stage", ArgumentDescription.of("Shows the day stage in a world."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the day stage for."))
                .permission("%s.command.time-dilation.day-stage".formatted(rootName))
                .handler(c -> handle(c, this::timeDilationDayStage)));
        manager.command(timeDilation
                .literal("handle", ArgumentDescription.of("Shows the handle for this feature."))
                .permission("%s.command.time-dilation.handle".formatted(rootName))
                .handler(c -> handle(c, this::timeDilationConfig)));

        var seasons = root
                .literal("seasons", ArgumentDescription.of("Seasons feature commands."));
        manager.command(seasons
                .literal("get", ArgumentDescription.of("Gets the current season at a location."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the season for."))
                .argument(KeyArgument.<CommandSender>newBuilder("biome")
                                .withSuggestionsProvider((ctx, inp) -> Stream.of(Biome.values()).map(biome -> biome.getKey().toString()).collect(Collectors.toList()))
                                .asOptional(), ArgumentDescription.of("The biome to get the season for."))
                .permission("%s.command.seasons.get".formatted(rootName))
                .handler(c -> handle(c, this::seasonsGet)));
        manager.command(seasons
                .literal("set", ArgumentDescription.of("Sets the time of a world to correspond with a season."))
                .argument(SeasonArgument.of(plugin, "season"), ArgumentDescription.of("The season to set to."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to set the season for."))
                .argument(KeyArgument.<CommandSender>newBuilder("biome")
                        .withSuggestionsProvider((ctx, inp) -> Stream.of(Biome.values()).map(biome -> biome.getKey().toString()).collect(Collectors.toList()))
                        .asOptional(), ArgumentDescription.of("The biome to set the season for."))
                .permission("%s.command.seasons.set".formatted(rootName))
                .handler(c -> handle(c, this::seasonsSet)));
        manager.command(seasons
                .literal("timeline", ArgumentDescription.of("Shows a timeline of all seasons for worlds."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to show for."))
                .permission("%s.command.seasons.timeline".formatted(rootName))
                .handler(c -> handle(c, this::seasonsTimeline)));

        var seasonsTime = seasons
                .literal("time", ArgumentDescription.of("Directly manages the raw time values of worlds."));
        manager.command(seasonsTime
                .literal("get", ArgumentDescription.of("Gets the raw time value of a world, in milliseconds."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the time for."))
                .permission("%s.command.seasons.time.get".formatted(rootName))
                .handler(c -> handle(c, this::seasonsTimeGet)));
        manager.command(seasonsTime
                .literal("set", ArgumentDescription.of("Sets the raw time value of a world."))
                .argument(LongArgument.of("time"), ArgumentDescription.of("The time to set to, in milliseconds."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to set the time for."))
                .permission("%s.command.seasons.time.set".formatted(rootName))
                .handler(c -> handle(c, this::seasonsTimeSet)));
    }

    private Component worldConfigName(Locale locale, String key) {
        return lc.safe(locale, PREFIX_COMMAND + ".handle.world." +
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

    private void timeDilationDayStage(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        var config = config(plugin.timeDilation());
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);
        send(sender, locale, "time_dilation.day_stage",
                "world", world.getName(),
                "day_stage", lc.safe(locale, "day_stage." + plugin.timeDilation().dayStage(world).key()));
    }

    private void timeDilationConfig(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        var config = config(plugin.timeDilation());
        send(sender, locale, "time_dilation.handle.worlds",
                "amount", config.worlds().handle().size()+"");
        for (var entry : config.worlds()) {
            var worldConfig = entry.getValue();
            send(sender, locale, "time_dilation.handle.world",
                    "name", worldConfigName(locale, entry.getKey()),
                    "day_duration", worldConfig.dayDuration().asDuration().toString(),
                    "day_factor", worldConfig.dayDuration().value()+"",
                    "night_duration", worldConfig.nightDuration().asDuration().toString(),
                    "night_factor", worldConfig.nightDuration().value()+"");
        }
    }

    private void seasonsGet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);
        Key biome = defaultedArg(ctx, "biome", pSender, p -> p.getLocation().getBlock().getBiome().getKey());

        config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            worldConfig.biomeConfig(biome).ifPresentOrElse(biomeConfig -> {
                biomeConfig.season(seasons.time(world)).ifPresentOrElse(season -> {
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
                seasons.seasonTime().put(world.getUID(), startsAt);
                send(sender, locale, "seasons.set",
                        "world", world.getName(),
                        "season", season.name(lc, locale),
                        "duration", Duration.duration(startsAt).toString(),
                        "ms", String.format(locale, "%,d", startsAt),
                        "was", String.format(locale, "%,d", was));
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
        int length = 30;

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
                        "elapsed", String.format(locale, "%.1f", elapsed / msPerMin),
                        "cycle", String.format(locale, "%.1f", cycle / msPerMin),
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
                "duration", Duration.duration(time).toString(),
                "ms", String.format(locale, "%,d", time));
    }

    private void seasonsTimeSet(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        Seasons seasons = plugin.seasons();
        var config = config(seasons);
        long time = ctx.get("time");
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);

        long was = seasons.time(world);
        seasons.seasonTime().put(world.getUID(), time);
        send(sender, locale, "seasons.time.set",
                "world", world.getName(),
                "duration", Duration.duration(time).toString(),
                "ms", String.format(locale, "%,d", time),
                "was", String.format(locale, "%,d", was));
    }
}
