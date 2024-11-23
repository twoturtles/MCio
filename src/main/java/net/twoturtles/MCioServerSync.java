package net.twoturtles;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

class MCioServerSync {
    private final Logger LOGGER = LogUtils.getLogger();
    MCioConfig config;

    public MCioServerSync(MCioConfig config) {
        this.config = config;

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
        });

    }

    void init(MinecraftServer server) {
        LOGGER.info("Server Started: Sync Mode");
        ServerTickManager tickManager = server.getTickManager();
        // 2147483647 / 1000 / 86400 = 24 days at 1000 TPS.
        // ServerTickManager stores the sprint steps as long, so could make a mixin
        // to pass in larger value.
        tickManager.startSprint(Integer.MAX_VALUE);
        tickManager.setFrozen(true);

        new TestThread(server);
    }


    class TestThread {
        private final Logger LOGGER = LogUtils.getLogger();
        private final MinecraftServer server;

        public TestThread(MinecraftServer server) {
            this.server = server;
            Thread thread = new Thread(this::threadRun, "MCio-TestThread");
            thread.start();
        }

        private void threadRun() {
            while (true) {
                ServerTickManager serverTickManager = server.getTickManager();
                serverTickManager.step(1);

                // XXX
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
