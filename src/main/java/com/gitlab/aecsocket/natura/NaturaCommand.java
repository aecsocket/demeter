package com.gitlab.aecsocket.natura;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.gitlab.aecsocket.natura.feature.Calendar;
import com.gitlab.aecsocket.natura.feature.Temperature;
import com.gitlab.aecsocket.unifiedframework.core.util.TextUtils;
import com.gitlab.aecsocket.unifiedframework.core.util.data.Tuple2;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@CommandAlias("natura|nat")
public class NaturaCommand extends BaseCommand {
    private final NaturaPlugin plugin;

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

    @CatchUnknown
    public void unknown(CommandSender sender) {
        send(sender, "command.unknown");
    }

    @HelpCommand
    @Description("Displays help for command usage.")
    @Syntax("")
    public void help(CommandSender sender, CommandHelp help) {
        send(sender, "command.help.header");
        help.getHelpEntries().forEach(entry -> send(sender, "command.help.entry",
                "command", entry.getCommand(),
                "syntax", entry.getParameterSyntax(),
                "description", entry.getDescription()));
    }

    @Subcommand("version|ver")
    @Description("Displays version info for Natura.")
    public void version(CommandSender sender) {
        PluginDescriptionFile description = plugin.getDescription();
        send(sender, "command.version",
                "name", description.getName(),
                "version", description.getVersion(),
                "authors", String.join(", ", description.getAuthors()));
    }

    @Subcommand("reload")
    @Description("Reloads all plugin data.")
    @CommandPermission("natura.command.reload")
    public void reload(CommandSender sender) {
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

    private Tuple2<WorldData, World> getData(CommandSender sender, World world) {
        if (sender instanceof Player && world == null) {
            world = ((Player) sender).getWorld();
        } else if (!(sender instanceof Player)) {
            send(sender, "command.error.specify_world");
            return Tuple2.of(null, world);
        }

        WorldData data = plugin.worldData(world);
        if (data == null) {
            send(sender, "command.error.no_world_data");
            return Tuple2.of(null, world);
        }

        return Tuple2.of(data, world);
    }

    @Subcommand("calendar get-season")
    @Description("Gets a world's season.")
    @CommandPermission("natura.command.calendar.get-season")
    @CommandCompletion("@worlds")
    @Syntax("[world]")
    public void calendarGetSeason(CommandSender sender, @Optional World world) {
        Tuple2<WorldData, World> tuple = getData(sender, world);
        WorldData data = tuple.a();
        world = tuple.b();
        if (data == null)
            return;

        Calendar.Season season = data.calendar().season();
        if (season == null)
            send(sender, "command.error.no_season");
        else
            send(sender, "command.calendar.get_season",
                    "world", world.getName(),
                    "season", season.getLocalizedName(plugin, locale(sender)));
    }

    @Subcommand("calendar list-seasons")
    @Description("Lists a world's seasons, in the order they will appear.")
    @CommandPermission("natura.command.calendar.list-seasons")
    @CommandCompletion("@worlds")
    @Syntax("[world]")
    public void calendarListSeasons(CommandSender sender, @Optional World world) {
        Tuple2<WorldData, World> tuple = getData(sender, world);
        WorldData data = tuple.a();
        world = tuple.b();
        if (data == null)
            return;

        Locale locale = locale(sender);
        List<Calendar.Season> seasons = data.calendar().settings().seasons;
        send(sender, "command.calendar.list_seasons.header",
                "world", world.getName());
        for (int i = 0; i < seasons.size(); i++) {
            send(sender, "command.calendar.list_seasons.entry",
                    "index", Integer.toString(i + 1),
                    "season", seasons.get(i).getLocalizedName(plugin, locale));
        }
    }

    @Subcommand("calendar set-season")
    @Description("Sets a world's season.")
    @CommandPermission("natura.command.calendar.set-season")
    @CommandCompletion("<season-index> @worlds")
    @Syntax("<season-index> [world]")
    public void calendarSetSeason(CommandSender sender, int seasonIndex, @Optional World world) {
        Tuple2<WorldData, World> tuple = getData(sender, world);
        WorldData data = tuple.a();
        world = tuple.b();
        if (data == null)
            return;

        Calendar calendar = data.calendar();
        List<Calendar.Season> seasons = calendar.settings().seasons;

        if (seasonIndex <= 0 || seasonIndex > seasons.size()) {
            send(sender, "command.error.invalid_index");
            return;
        }

        --seasonIndex;
        Calendar.Season toSet = seasons.get(seasonIndex);
        calendar.state().seasonTicks = 0;
        calendar.state().seasonIndex = seasonIndex;
        calendar.updateSeason();
        send(sender, "command.calendar.set_season",
                "world", world.getName(),
                "season", toSet.getLocalizedName(plugin, locale(sender)));
    }

    @Subcommand("temperature factors")
    @Description("Gets the factors involved in the temperature calculation.")
    @CommandPermission("natura.command.temperature.factors")
    @CommandCompletion("@players")
    @Syntax("[player]")
    public void temperatureFactors(CommandSender sender, @Optional Player target) {
        if (target == null) {
            if (sender instanceof Player)
                target = (Player) sender;
            else {
                send(sender, "command.error.sender_not_player");
                return;
            }
        }

        Locale locale = locale(sender);
        World world = target.getWorld();
        Tuple2<WorldData, World> tuple = getData(sender, world);
        WorldData data = tuple.a();
        if (data == null)
            return;

        Temperature feature = data.temperature();
        double temperature = feature.baseTemperature(target);
        send(sender, "command.temperature.factors.base",
                "temperature", String.format(locale, "%.2f", temperature));
        for (Temperature.Factor factor : feature.factors()) {
            double current = factor.apply(feature, target, temperature);
            double delta = current - temperature;
            temperature = current;
            send(sender, "command.temperature.factors.entry",
                    "type", factor.getClass().getSimpleName(),
                    "temperature", String.format(locale, "%.2f", delta));
        }
        send(sender, "command.temperature.factors.total",
                "temperature", String.format(locale, "%.2f", temperature));
    }
}
