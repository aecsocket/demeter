package com.gitlab.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.context.CommandContext;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Locale;

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
}
