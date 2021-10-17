package com.gitlab.aecsocket.demeter.paper;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.context.CommandContext;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
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
                .literal("config", ArgumentDescription.of("Shows the config for this feature."))
                .permission("%s.command.time-dilation.config".formatted(rootName))
                .handler(c -> handle(c, this::timeDilationConfig)));
    }

    private Component worldConfigName(Locale locale, String key) {
        return lc.safe(locale, PREFIX_COMMAND + ".config.world." +
                (WorldsConfig.DEFAULT.equals(key) ? "default" : "normal"),
                "key", key);
    }

    private void timeDilationConfig(CommandContext<CommandSender> ctx, CommandSender sender, Locale locale, @Nullable Player pSender) {
        var config = plugin.timeDilation().config;
        if (config == null)
            throw error("no_config",
                    "feature", "time_dilation");
        send(sender, locale, "time_dilation.config.worlds",
                "amount", config.worlds().handle().size()+"");
        for (var entry : config.worlds()) {
            var worldConfig = entry.getValue();
            send(sender, locale, "time_dilation.config.world",
                    "name", worldConfigName(locale, entry.getKey()),
                    "duration", worldConfig.duration().asDuration().toString(),
                    "factor", worldConfig.duration().value()+"");
        }
    }
}
