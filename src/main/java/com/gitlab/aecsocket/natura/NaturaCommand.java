package com.gitlab.aecsocket.natura;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.gitlab.aecsocket.unifiedframework.core.util.TextUtils;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    private void sendError(CommandSender sender, Throwable e, String key, Object... args) {
        send(sender, key, args);
        plugin.log(LogLevel.WARN, e, e.getMessage());
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

    @Subcommand("calendar get-season")
    @Description("Gets a world's season.")
    @CommandPermission("natura.command.calendar.get-season")
    @CommandCompletion("@worlds")
    @Syntax("[world]")
    public void calendarGetSeason(CommandSender sender, @Optional World world) {
        if (sender instanceof Player && world == null) {
            world = ((Player) sender).getWorld();
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("must have a world br"));
            return;
        }

        WorldData data = plugin.worldData(world);
        if (data == null) {
            sender.sendMessage(Component.text("no world data"));
            return;
        }

        sender.sendMessage(Component.text("season = " + data.calendar().season()));
    }
}
