package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

class MCioServerAsync {
    private final Logger LOGGER = LogUtils.getLogger();
    MCioConfig config;

    public MCioServerAsync(MCioConfig config) {
        this.config = config;

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.init(server);
        });
    }

    void init(MinecraftServer server) {
        LOGGER.info("Server Started: Async Mode");
    }
}
