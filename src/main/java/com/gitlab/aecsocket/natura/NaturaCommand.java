package com.gitlab.aecsocket.natura;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.arguments.standard.EnumArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.bukkit.arguments.selector.MultiplePlayerSelector;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.bukkit.parsers.location.LocationArgument;
import cloud.commandframework.bukkit.parsers.selector.MultiplePlayerSelectorArgument;
import cloud.commandframework.captions.FactoryDelegatingCaptionRegistry;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.minecraft.extras.MinecraftHelp;
import cloud.commandframework.paper.PaperCommandManager;
import com.gitlab.aecsocket.natura.feature.BodyTemperature;
import com.gitlab.aecsocket.natura.feature.Climate;
import com.gitlab.aecsocket.natura.feature.Seasons;
import com.gitlab.aecsocket.natura.util.SeasonArgument;
import com.gitlab.aecsocket.natura.util.Timeline;
import com.gitlab.aecsocket.unifiedframework.core.util.TextUtils;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.minecraft.server.v1_16_R3.BiomeBase;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class NaturaCommand {
    public static final String SYMBOL_THIS = ".";

    private final NaturaPlugin plugin;
    private MinecraftHelp<CommandSender> help;

    public NaturaCommand(NaturaPlugin plugin) {
        this.plugin = plugin;
    }

    public NaturaPlugin plugin() { return plugin; }

    private Locale locale(CommandSender sender) { return plugin.locale(sender); }

    private Component gen(CommandSender sender, String key, Object... args) {
        return plugin.gen(locale(sender), key, args);
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(gen(sender, key, args));
    }

    protected void register(PaperCommandManager<CommandSender> manager) {
        manager.registerBrigadier();
        manager.registerAsynchronousCompletions();
        manager.getParserRegistry().registerParserSupplier(TypeToken.get(Seasons.class), params -> new SeasonArgument.SeasonParser<>(plugin));
        FactoryDelegatingCaptionRegistry<CommandSender> captions = (FactoryDelegatingCaptionRegistry<CommandSender>) manager.getCaptionRegistry();
        captions.registerMessageFactory(SeasonArgument.ARGUMENT_PARSE_FAILURE_SEASON, (cap, sender) -> "'{input}' is not a valid season");

        BukkitAudiences audiences = BukkitAudiences.create(plugin);
        help = new MinecraftHelp<>("/natura help", audiences::sender, manager);

        BiFunction<CommandContext<CommandSender>, String, List<String>> suggestSeasons = (ctx, arg) ->
                StringUtil.copyPartialMatches(arg, plugin.seasons().config().seasons.keySet(), new ArrayList<>());

        Command.Builder<CommandSender> cmdRoot = manager.commandBuilder("natura", ArgumentDescription.of("Natura's main command."), "nat");
        Command.Builder<CommandSender> cmdClimate = cmdRoot
                .literal("climate", ArgumentDescription.of("Commands involving the climate feature."));
        Command.Builder<CommandSender> cmdSeasons = cmdRoot
                .literal("seasons", ArgumentDescription.of("Commands involving the seasons feature."));
        Command.Builder<CommandSender> cmdBodyTemperature = cmdRoot
                .literal("body-temperature", ArgumentDescription.of("Commands involving the body temperature feature."));

        manager.command(cmdRoot
                .literal("help")
                .argument(StringArgument.optional("query", StringArgument.StringMode.GREEDY))
                .handler(ctx -> help.queryCommands(ctx.getOrDefault("query", ""), ctx.getSender()))
        );
        manager.command(cmdRoot
                .literal("version", ArgumentDescription.of("Gets version information."))
                .handler(this::version)
        );
        manager.command(cmdRoot
                .literal("reload", ArgumentDescription.of("Reloads all plugin data."))
                .permission("natura.command.reload")
                .handler(this::reload)
        );
        manager.command(cmdRoot
                .literal("biomes", ArgumentDescription.of("Dumps information about biomes."))
                .argument(EnumArgument.optional(Biome.class, "biome"), ArgumentDescription.of("The biome to dump information about."))
                .permission("natura.command.biomes")
                .handler(this::biomes)
        );

        manager.command(cmdClimate
                .literal("get", ArgumentDescription.of("Gets climate information about a block."))
                .argument(LocationArgument.optional("location"))
                .permission("natura.command.climate.get")
                .handler(this::climateGet)
        );

        manager.command(cmdSeasons
                .literal("get", ArgumentDescription.of("Gets the season."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the season for."))
                .argument(EnumArgument.optional(Biome.class, "biome"), ArgumentDescription.of("The biome to get the season for."))
                .permission("natura.command.seasons.get")
                .handler(this::seasonsGet)
        );
        manager.command(cmdSeasons
                .literal("set", ArgumentDescription.of("Sets the time of a world in accordance with a specific season."))
                .argument(SeasonArgument.<CommandSender>newBuilder("season", plugin).withSuggestionsProvider(suggestSeasons).build())
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the season for."))
                .argument(EnumArgument.optional(Biome.class, "biome"), ArgumentDescription.of("The biome to get the season for."))
                .permission("natura.command.seasons.set")
                .handler(this::seasonsSet)
        );
        manager.command(cmdSeasons
                .literal("timeline", ArgumentDescription.of("Displays a timeline of seasons."))
                .argument(WorldArgument.optional("world"), ArgumentDescription.of("The world to get the season for."))
                .argument(EnumArgument.optional(Biome.class, "biome"), ArgumentDescription.of("The biome to get the season for."))
                .permission("natura.command.seasons.timeline")
                .handler(this::seasonsTimeline)
        );

        manager.command(cmdBodyTemperature
                .literal("factors", ArgumentDescription.of("Shows the factors involved in calculating body temperature."))
                .argument(MultiplePlayerSelectorArgument.optional("targets"), ArgumentDescription.of("The player(s) to get the factors for."))
                .permission("natura.command.body-temperature.factors")
                .handler(this::bodyTemperatureFactors)
        );
    }

    private void version(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        PluginDescriptionFile desc = plugin.getDescription();
        send(sender, "command.version",
                "name", desc.getName(),
                "version", desc.getVersion(),
                "authors", String.join(", ", desc.getAuthors()));
    }

    private void reload(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        send(sender, "command.reload.start");
        AtomicInteger warnings = new AtomicInteger(0);
        List<LoggingEntry> result = new ArrayList<>();
        plugin.load(result);
        plugin.log(result).forEach(entry -> {
            if (entry.level().level() >= LogLevel.WARN.level()) {
                warnings.incrementAndGet();
                for (String line : TextUtils.lines(entry.infoBasic())) {
                    send(sender, "command.reload.warning",
                            "message", line);
                }
            }
        });
        if (warnings.get() == 0)
            send(sender, "command.reload.no_warnings");
        else
            send(sender, "command.reload.warnings",
                    "warnings", Integer.toString(warnings.get()));
    }

    public void biomes(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        Biome biome = (Biome) ctx.getOptional("biome").orElse(null);
        Map<Biome, BiomeBase> toDump = biome == null ? plugin.internalBiomes() : Collections.singletonMap(biome, plugin.internalBiome(biome));

        for (var entry : toDump.entrySet()) {
            Biome key = entry.getKey();
            BiomeBase value = entry.getValue();
            if (value == null) {
                send(sender, "command.biomes.no_value",
                        "key", key.getKey().toString());
            } else {
                send(sender, "command.biomes.value",
                        "key", key.getKey().toString(),
                        "enum", key.name(),
                        "temperature", Objects.toString(value.k()),
                        "humidity", Objects.toString(value.getHumidity()));
            }
        }
    }

    private void climateGet(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        Player player = sender instanceof Player ? ((Player) sender) : null;
        Location location = ctx.getOrDefault("location", player == null ? null : player.getLocation());
        if (location == null) {
            send(sender, "command.error.no_location");
            return;
        }

        Block block = location.getBlock();
        Climate climate = plugin.climate();
        send(sender, "command.climate.get.total",
                "temperature", String.format("%.03f", climate.temperature(block)),
                "humidity", String.format("%.03f", climate.humidity(block)));
        send(sender, "command.climate.get.base",
                "temperature", String.format("%.03f", climate.baseTemperature(block)),
                "humidity", String.format("%.03f", climate.baseHumidity(block)));
        Climate.WorldState state = climate.state(block.getWorld());
        send(sender, "command.climate.get.noise",
                "temperature", String.format("%.03f", state.temperature(block.getX(), block.getZ())),
                "humidity", String.format("%.03f", state.humidity(block.getX(), block.getZ())));
    }

    private void seasonsGet(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        Player player = sender instanceof Player ? ((Player) sender) : null;
        World world = ctx.getOrDefault("world", player == null ? null : player.getWorld());
        Biome biome = ctx.getOrDefault("biome", player == null ? null : player.getLocation().getBlock().getBiome());
        if (world == null) {
            send(sender, "command.error.no_world");
            return;
        }
        if (biome == null) {
            send(sender, "command.error.no_biome");
            return;
        }

        Seasons seasons = plugin.seasons();
        Seasons.BiomeConfig biomeConfig = seasons.config(world).config(biome);
        Seasons.Season season = biomeConfig.season(seasons.time(world));
        Locale locale = locale(sender);
        if (season == null)
            send(sender, "command.seasons.get.none",
                    "world", world.getName(),
                    "biome", biome.getKey().toString());
        else {
            long duration = biomeConfig.durations.get(season);
            long time = seasons.time(world) % duration;
            send(sender, "command.seasons.get.season",
                    "world", world.getName(),
                    "biome", biome.getKey().toString(),
                    "season", season.localizedName(plugin, locale),
                    "progress_ticks", Long.toString(time),
                    "duration_ticks", Long.toString(duration),
                    "progress_percent", String.format(locale, "%.1f", ((double) time / duration) * 100));
        }
    }

    private void seasonsSet(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        Player player = sender instanceof Player ? ((Player) sender) : null;
        Seasons.Season season = ctx.get("season");
        World world = ctx.getOrDefault("world", player == null ? null : player.getWorld());
        Biome biome = ctx.getOrDefault("biome", player == null ? null : player.getLocation().getBlock().getBiome());
        if (world == null) {
            send(sender, "command.error.no_world");
            return;
        }
        if (biome == null) {
            send(sender, "command.error.no_biome");
            return;
        }

        Seasons seasons = plugin.seasons();
        Seasons.WorldConfig worldConfig = seasons.config(world);
        Seasons.BiomeConfig biomeConfig = worldConfig.config(biome);

        long startsAt = biomeConfig.startsAt(season);
        if (startsAt == -1) {
            send(sender, "command.error.unconfigured_season");
            return;
        }

        long time = world.getFullTime();
        long cycleLength = worldConfig.timeCycleLength;
        long cycles = time / cycleLength;
        world.setFullTime((cycleLength * cycles) + biomeConfig.startsAt(season));

        Locale locale = locale(sender);
        send(sender, "command.seasons.set",
                "world", world.getName(),
                "season", season.localizedName(plugin, locale),
                "new_time", String.format(locale, "%,d", world.getFullTime()),
                "old_time", String.format(locale, "%,d", time));
    }

    private void seasonsTimeline(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        World world = (World) ctx.getOptional("world").orElse(null);
        Biome biome = (Biome) ctx.getOptional("biome").orElse(null);
        Collection<World> allWorlds = world == null ? Bukkit.getWorlds() : Collections.singleton(world);

        Seasons seasons = plugin.seasons();
        Locale locale = locale(sender);
        int length = 50;

        for (World tWorld : allWorlds) {
            Seasons.WorldConfig cfg = seasons.config(tWorld);
            long elapsed = seasons.time(tWorld);
            long cycle = cfg.timeCycleLength;
            double progress = (double) elapsed / cycle;
            send(sender, "command.seasons.timeline.world",
                    "timeline", new Timeline(length)
                            .complete(progress)
                            .build(),
                    "world", tWorld.getName(),
                    "elapsed", String.format(locale, "%.1f", (double) elapsed / NaturaPlugin.TICKS_PER_DAY),
                    "cycle", String.format(locale, "%.1f", (double) cycle / NaturaPlugin.TICKS_PER_DAY),
                    "percent", String.format(locale, "%.1f", progress * 100)
            );

            for (Seasons.BiomeConfig cfg2 : biome == null ? cfg.biomes : Collections.singleton(cfg.config(biome))) {
                Timeline timeline = new Timeline(length)
                        .complete(progress);
                for (Seasons.Season season : cfg2.seasons) {
                    timeline.addSections(new Timeline.Section(
                            season.color, (double) cfg2.durations.get(season) / cycle
                    ));
                }
                send(sender, "command.seasons.timeline.entry",
                        "timeline", timeline.build(),
                        "season", cfg2.season(elapsed).localizedName(plugin, locale)
                );
            }
        }
    }

    private Component temp(CommandSender sender, double temperature) {
        String formatted = String.format("%.02f", temperature);
        if (temperature > 0)
            return gen(sender, "temperature.positive",
                    "temperature", formatted);
        if (temperature < 0)
            return gen(sender, "temperature.negative",
                    "temperature", formatted);
        return gen(sender, "temperature.neutral",
                "temperature", formatted);
    }

    private void bodyTemperatureFactors(CommandContext<CommandSender> ctx) {
        CommandSender sender = ctx.getSender();
        MultiplePlayerSelector selector = ctx.getOrDefault("targets", null);
        List<Player> targets = selector == null
                ? sender instanceof Player ? Collections.singletonList((Player) sender) : null
                : selector.getPlayers();
        if (targets == null) {
            send(sender, "command.error.no_players");
            return;
        }

        BodyTemperature bodyTemperature = plugin.bodyTemperature();
        for (Player player : targets) {
            send(sender, "command.body_temperature.factors.header",
                    "player", player.displayName(),
                    "current", temp(sender, bodyTemperature.current(player)),
                    "target", temp(sender, bodyTemperature.target(player)));

            double value = 0;
            for (BodyTemperature.Factor factor : bodyTemperature.factors()) {
                double old = value;
                value = factor.apply(bodyTemperature, player, value);
                send(sender, "command.body_temperature.factors.entry",
                        "class", factor.getClass().getSimpleName(),
                        "old", temp(sender, old),
                        "new", temp(sender, value),
                        "delta", temp(sender, value - old));
            }
        }
    }
}
