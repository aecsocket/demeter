package com.gitlab.aecsocket.demeter;

import cloud.commandframework.ArgumentDescription;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;

/* package */ class DemeterCommand extends BaseCommand<DemeterPlugin> {
    public DemeterCommand(DemeterPlugin plugin) throws Exception {
        super(plugin, "demeter",
                (mgr, root) -> mgr.commandBuilder(root, ArgumentDescription.of("Plugin main command."), "dem"));
    }
}
