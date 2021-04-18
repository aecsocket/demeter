package com.gitlab.aecsocket.natura;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.commands.annotation.Optional;
import com.gitlab.aecsocket.natura.feature.Feature;
import com.gitlab.aecsocket.natura.feature.Seasons;
import com.gitlab.aecsocket.natura.feature.Temperature;
import com.gitlab.aecsocket.natura.util.Timeline;
import com.gitlab.aecsocket.unifiedframework.core.util.TextUtils;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.gitlab.aecsocket.natura.NaturaPlugin.plugin;

@CommandAlias("natura|nat")
public class NaturaCommand extends BaseCommand {
    public static final String SYMBOL_THIS = ".";

    private Locale locale(CommandSender sender) { return plugin().locale(sender); }

    private Component gen(CommandSender sender, String key, Object... args) {
        return plugin().gen(locale(sender), key, args);
    }

    private void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(gen(sender, key, args));
    }

    protected void initManager(PaperCommandManager commandManager) {
        commandManager.enableUnstableAPI("help");
        commandManager.getCommandCompletions().registerCompletion("worlds", ctx -> {
            List<String> result = new ArrayList<>();
            result.add(NaturaCommand.SYMBOL_THIS);
            result.addAll(Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
            return result;
        });
        commandManager.getCommandContexts().registerIssuerAwareContext(World.class, ctx -> {
            String arg = ctx.popFirstArg();
            CommandSender sender = ctx.getSender();
            if (arg == null)
                return null;
            if (NaturaCommand.SYMBOL_THIS.equals(arg)) {
                if (ctx.getPlayer() == null) {
                    send(sender, "command.error.sender_using_this");
                    throw new InvalidCommandArgument(false);
                }
                return ctx.getPlayer().getWorld();
            }

            World world = Bukkit.getWorld(arg);
            if (world == null) {
                send(sender, "command.error.invalid_world",
                        "world", arg);
                throw new InvalidCommandArgument(false);
            }
            return world;
        });
        commandManager.getCommandCompletions().registerCompletion("biomes", ctx -> {
            List<String> result = new ArrayList<>();
            result.add(NaturaCommand.SYMBOL_THIS);
            Registry.BIOME.forEach(biome -> result.add(biome.getKey().value()));
            return result;
        });
        commandManager.getCommandContexts().registerIssuerAwareContext(Biome.class, ctx -> {
            String arg = ctx.popFirstArg();
            CommandSender sender = ctx.getSender();
            if (arg == null)
                return null;
            if (NaturaCommand.SYMBOL_THIS.equals(arg)) {
                if (ctx.getPlayer() == null) {
                    send(sender, "command.error.sender_using_this");
                    throw new InvalidCommandArgument(false);
                }
                return ctx.getPlayer().getLocation().getBlock().getBiome();
            }

            NamespacedKey key = NamespacedKey.minecraft(arg);
            Biome biome = Registry.BIOME.get(key);
            if (biome == null) {
                send(sender, "command.error.invalid_biome",
                        "biome", arg);
                throw new InvalidCommandArgument(false);
            }
            return biome;
        });
    }

    @Subcommand("reload")
    @Description("Reloads all plugin data.")
    @CommandPermission("natura.command.reload")
    @CommandCompletion("[save]")
    @Syntax("[save]")
    public void reload(CommandSender sender, @Default(value = "true") boolean save) {
        send(sender, "command.save.start");
        if (save) {
            plugin().save();
        }
        send(sender, "command.reload.start");
        AtomicInteger warnings = new AtomicInteger(0);
        List<LoggingEntry> result = new ArrayList<>();
        plugin().load(result);
        plugin().log(result).forEach(entry -> {
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

    @Subcommand("save")
    @Description("Reloads all plugin data.")
    @CommandPermission("natura.command.reload")
    @CommandCompletion("[save]")
    @Syntax("[save]")
    public void save(CommandSender sender) {
        send(sender, "command.save.start");
        plugin().save();
        send(sender, "command.save.stop");
    }

    private <T extends Feature> T feature(CommandSender sender, String id) {
        T feature = plugin().feature(id);
        if (feature == null) {
            send(sender, "command.error.feature_disabled");
        }
        return feature;
    }

    @Subcommand("seasons timeline")
    @Description("Displays a timeline of seasons in a specified world.")
    @CommandPermission("natura.command.seasons.timeline")
    @CommandCompletion("@worlds @biomes")
    @Syntax("[world|.] [biome|.]")
    public void seasonsTimeline(CommandSender sender, @Optional World world, @Optional Biome biome) {
        Seasons feature = feature(sender, Seasons.ID);
        if (feature == null)
            return;

        Locale locale = locale(sender);
        int length = 50;
        double cycleProgress = feature.cycleProgress();
        send(sender, "command.seasons.timeline.header",
                "timeline", new Timeline(length)
                        .complete(cycleProgress)
                        .build(),
                "elapsed", String.format(locale, "%.1f", feature.state().cycleElapsed / (double) plugin().ticksPerDay()),
                "total", String.format(locale, "%.1f", feature.config().cycleDuration),
                "percent", String.format(locale, "%.1f", feature.cycleProgress() * 100)
        );

        Map<World, List<Seasons.BiomeData>> timelines = new HashMap<>();
        for (World target : world == null ? Bukkit.getWorlds() : Collections.singletonList(world)) {
            feature.config().config(target).ifPresent(config -> {
                List<Seasons.BiomeData> toAdd = new ArrayList<>();
                if (biome == null)
                    toAdd = config.biomes;
                else
                    config.biomeData(biome).ifPresent(toAdd::add);

                if (toAdd.size() > 0) {
                    timelines.computeIfAbsent(target, __ -> new ArrayList<>()).addAll(toAdd);
                }
            });
        }

        for (var entry : timelines.entrySet()) {
            send(sender, "command.seasons.timeline.world",
                    "world", entry.getKey().getName());
            for (Seasons.BiomeData timeline : entry.getValue()) {
                Timeline display = new Timeline(length)
                        .complete(cycleProgress);
                for (Seasons.Season season : timeline.mappedSeasons) {
                    Color color = season.color;
                    display.addSections(new Timeline.Section(
                            TextColor.color(color.getRed(), color.getGreen(), color.getBlue()),
                            season.cycleWeight / (double) timeline.totalWeight
                    ));
                }
                send(sender, "command.seasons.timeline.season",
                        "timeline", display.build(),
                        "season", timeline.currentSeason(cycleProgress).getLocalizedName(locale)
                );
            }
        }
    }

    @Subcommand("seasons set")
    @Description("Sets the current season clock.")
    @CommandPermission("natura.command.seasons.set")
    @CommandCompletion("[days]")
    @Syntax("[days]")
    public void seasonsSet(CommandSender sender, double days) {
        Seasons feature = feature(sender, Seasons.ID);
        if (feature == null)
            return;

        feature.state().cycleElapsed = (long) (days * plugin().ticksPerDay());
    }

    private Component tempComponent(CommandSender sender, double temperature) {
        return gen(sender, "command.temperature.temperature." + (temperature == 0 ? "neutral" : temperature > 0 ? "hot" : "cold"),
                "temperature", String.format(locale(sender), "%.2f", temperature));
    }

    @Subcommand("temperature factors")
    @Description("Breaks down the factors involved in getting the temperature of a player.")
    @CommandPermission("natura.command.temperature.factors")
    @CommandCompletion("@players")
    @Syntax("[player]")
    public void temperatureFactors(CommandSender sender, @Optional Player player) {
        Temperature feature = feature(sender, Temperature.ID);
        if (feature == null)
            return;

        if (player == null) {
            if (sender instanceof Player) {
                player = (Player) sender;
            } else {
                send(sender, "command.error.unspecified_player");
                return;
            }
        }

        Locale locale = locale(sender);
        double temperature = feature.baseTemperature(player.getLocation().getBlock());
        send(sender, "command.temperature.factors.base",
                "temperature", tempComponent(sender, temperature));
        for (Temperature.Factor factor : feature.factors()) {
            double next = factor.apply(feature, player, temperature);
            double delta = next - temperature;
            send(sender, "command.temperature.factors.entry",
                    "type", factor.getClass().getSimpleName(),
                    "temperature", tempComponent(sender, delta));
            temperature = next;
        }
        send(sender, "command.temperature.factors.total",
                "temperature", tempComponent(sender, temperature));
    }
}
