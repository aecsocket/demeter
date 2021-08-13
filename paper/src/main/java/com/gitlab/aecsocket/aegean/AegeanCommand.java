package com.gitlab.aecsocket.aegean;

import cloud.commandframework.ArgumentDescription;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;

/* package */ class AegeanCommand extends BaseCommand<AegeanPlugin> {
    public AegeanCommand(AegeanPlugin plugin) throws Exception {
        super(plugin, "aegean",
                (mgr, root) -> mgr.commandBuilder(root, ArgumentDescription.of("Plugin main command."), "aeg"));
    }
}
