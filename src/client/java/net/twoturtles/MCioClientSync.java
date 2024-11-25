package net.twoturtles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.integrated.IntegratedServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Optional;

public class MCioClientSync {
    private final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftClient client;
    private MCioConfig config;

    private final MCioNetworkConnection connection;
    private final MCioActionHandler actionHandler;
    private final MCioObservationHandler observationHandler;

    private boolean gameRunning = false;
    private boolean waitingForFirstAction = true;
    private int lastActionSequence = 0;

    MCioClientSync(MCioConfig config) {
        client = MinecraftClient.getInstance();
        this.config = config;

        connection = new MCioNetworkConnection();
        actionHandler = new MCioActionHandler(client);
        observationHandler = new MCioObservationHandler(client, config);

        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
            clientStep();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            serverStep();
        });

        //new TestThread();
    }

    void clientStep() {
        // XXX Make window responsive while waiting for an action. At least allow it to be brought to the foreground.
        // XXX Why can't I double jump to fly in creative mode?

        if (!gameRunning) {
            return;
        }
        if (waitingForFirstAction) {
            LOGGER.info("Waiting for first action");
        }
        Optional<ActionPacket> optAction = connection.recvActionPacket();
        if (optAction.isEmpty()) {
            LOGGER.warn("Invalid action");
            return;
        }

        if (waitingForFirstAction) {
            LOGGER.info("Received first action");
            waitingForFirstAction = false;
        }
        ActionPacket action = optAction.get();
        LOGGER.debug("ACTION {}", action);
        actionHandler.processAction(action);
    }

    void serverStep() {
        IntegratedServer server = client.getServer();
        if (server != null) {
            server.execute(() -> {
                ServerTickManager serverTickManager = server.getTickManager();
                serverTickManager.step(1);
            });
        }

        // XXX Server is on a different thread. Need some synchronization
        Optional<ObservationPacket> optObservation = observationHandler.collectObservation(lastActionSequence);
        if (optObservation.isPresent()) {
            LOGGER.debug("OBSERVATION {}", optObservation.get());
            gameRunning = true;
        }
        optObservation.ifPresent(connection::sendObservationPacket);
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
