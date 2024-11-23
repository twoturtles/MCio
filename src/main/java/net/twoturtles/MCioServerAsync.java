package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

class MCioServerAsync {
    private final Logger LOGGER = LogUtils.getLogger();
    MCioConfig config;

    public MCioServerAsync(MCioConfig config) {
        this.config = config;

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
        });

    }

    void init(MinecraftServer server) {
        LOGGER.info("Server Started: Async Mode");
    }
}
