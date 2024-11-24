package net.twoturtles;

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
