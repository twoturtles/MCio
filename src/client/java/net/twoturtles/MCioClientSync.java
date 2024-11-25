package net.twoturtles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.integrated.IntegratedServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class MCioClientSync {
    private final Logger LOGGER = LogUtils.getLogger();
    MCioConfig config;

    MCioClientSync(MCioConfig config) {
        this.config = config;

        //new TestThread();
    }

    void stop() { }

    // XXX Run steps as fast as possible
    /*
    [16:51:01] [Server thread/INFO] (TrackPerSecond) ServerTicks per-second=38.7
    [16:51:01] [Render thread/INFO] (TrackPerSecond) ClientTicks per-second=109.5
    [16:51:02] [Render thread/INFO] (TrackPerSecond) Frames per-second=110.1
    [16:51:02] [Render thread/INFO] (TrackPerSecond) FrameCaptures per-second=110.1
     */
    class TestThread {
        private final Logger LOGGER = LogUtils.getLogger();

        public TestThread() {
            Thread thread = new Thread(this::threadRun, "MCio-TestThread");
            thread.start();
        }

        private void threadRun() {
            while (true) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    IntegratedServer server = client.getServer();
                    if (server != null) {
                        server.execute(() -> {
                            ServerTickManager serverTickManager = server.getTickManager();
                            serverTickManager.step(1);
                        });
                    }
                }

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
