package com.gitlab.aecsocket.demeter;

import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import com.gitlab.aecsocket.minecommons.paper.plugin.BasePlugin;

public class DemeterPlugin extends BasePlugin<DemeterPlugin> {
    public static final int BSTATS_ID = 13021;

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    protected DemeterCommand createCommand() throws Exception {
        return new DemeterCommand(this);
    }
}
