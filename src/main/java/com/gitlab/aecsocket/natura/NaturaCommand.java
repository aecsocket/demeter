package com.gitlab.aecsocket.natura;

import cloud.commandframework.ArgumentDescription;
import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;

/* package */ class NaturaCommand extends BaseCommand<NaturaPlugin> {
    public NaturaCommand(NaturaPlugin plugin) throws Exception {
        super(plugin, "natura",
                (mgr, root) -> mgr.commandBuilder(root, ArgumentDescription.of("Plugin main command."), "nat"));
    }
}
