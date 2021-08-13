package com.gitlab.aecsocket.aegean;

import com.gitlab.aecsocket.minecommons.paper.plugin.BaseCommand;
import com.gitlab.aecsocket.minecommons.paper.plugin.BasePlugin;

public class AegeanPlugin extends BasePlugin<AegeanPlugin> {
    public static final int BSTATS_ID = 12418;

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    protected BaseCommand<AegeanPlugin> createCommand() throws Exception {
        return new AegeanCommand(this);
    }
}
