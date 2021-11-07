package com.gitlab.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.context.CommandContext;
import com.gitlab.aecsocket.demeter.paper.util.KeyArgument;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* package */ class DemeterCommand extends BaseCommand<DemeterPlugin> {
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
        var config = config(plugin.seasons());
        World world = defaultedArg(ctx, "world", pSender, Entity::getWorld);
        Key biome = defaultedArg(ctx, "biome", pSender, p -> p.getLocation().getBlock().getBiome().getKey());

        config.worlds.get(world).ifPresentOrElse(worldConfig -> {
            worldConfig.biomeConfig(biome).ifPresentOrElse(biomeConfig -> {
                biomeConfig.season(worldConfig.time(world)).ifPresentOrElse(season -> {
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
}
