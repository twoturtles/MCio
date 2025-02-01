package net.twoturtles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.twoturtles.mixin.ServerTickManagerAccessor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

class MCioServerSync {
    private final Logger LOGGER = LogUtils.getLogger();
    private final MCioSyncUtil syncUtil = MCioSyncUtil.getInstance();
    private MCioConfig config;

    public MCioServerSync(MCioConfig config) {
        this.config = config;
        ServerLifecycleEvents.SERVER_STARTED.register(this::init);
        ServerTickEvents.START_SERVER_TICK.register(this::startTickCB);
        ServerTickEvents.END_SERVER_TICK.register(this::endTickCB);
    }

    void init(MinecraftServer server) {
        // For sync mode run Minecraft in sprint mode. This way there's no artificial delay between ticks.
        // It will go as fast as we step.
        ServerTickManager tickManager = server.getTickManager();
        // Start the sprint with the normal API, then set the sprint to go forever.
        tickManager.startSprint(1);
        ((ServerTickManagerAccessor) tickManager).setSprintTicks(Long.MAX_VALUE);
        ((ServerTickManagerAccessor) tickManager).setScheduledSprintTicks(Long.MAX_VALUE);
    }

    void startTickCB(MinecraftServer server) {
        syncUtil.serverStartTick();
    }
    void endTickCB(MinecraftServer server) {
        syncUtil.serverEndTick();
    }

    void stop() { }

}
