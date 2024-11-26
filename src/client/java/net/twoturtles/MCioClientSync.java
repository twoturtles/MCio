package net.twoturtles;

import java.util.Optional;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.integrated.IntegratedServer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;


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
    private int ticks = 0;

    /*
     * Order of events:
     * START_CLIENT_TICK -> wait -> process action -> client tick -> END_CLIENT_TICK ->
     *      server_step (unknown completion) -> Render -> Capture callback -> generate observation
     *
     */
    MCioClientSync(MCioConfig config) {
        client = MinecraftClient.getInstance();
        this.config = config;

        connection = new MCioNetworkConnection();
        actionHandler = new MCioActionHandler(client);
        observationHandler = new MCioObservationHandler(client, config);

        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
            ticks++;
            checkGameRunning(client_cb);
            processAction();
        });

        MCioFrameCapture frameCapture = MCioFrameCapture.getInstance();
        frameCapture.registerCaptureCallback(frame -> {
            if (!gameRunning) {
                return;
            }

            // Capture happens just before swapBuffers. Client ticks happen before the render.
            // So this happens after the end of the client tick.
            generateObservation();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            serverStep();
        });

        //new TestThread();
    }

    void checkGameRunning(MinecraftClient client) {
        if (gameRunning) {
            return;
        }
        // XXX This still passes a few frames before the game it up.
        // I believe these will happen at the same time, but check.
        if (client.world != null && client.player != null) {
            gameRunning = true;
        }
    }

    void processAction() {
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
        lastActionSequence = action.sequence();
        LOGGER.info("ACTION {}", action);
        actionHandler.processAction(action);
    }

    // XXX When should this happen? At the end of a tick? After the action?
    void serverStep() {
        if (!gameRunning) {
            return;
        }

        // XXX Server is on a different thread. Need some synchronization
        IntegratedServer server = client.getServer();
        if (server != null) {
            server.execute(() -> {
                ServerTickManager serverTickManager = server.getTickManager();
                serverTickManager.step(1);
            });
        }
    }

    // XXX Ideally this would include the update from the server
    void generateObservation() {
        if (!gameRunning) {
            return;
        }
        Optional<ObservationPacket> optObservation = observationHandler.collectObservation(lastActionSequence);
        if (optObservation.isEmpty()) {
            // client.player is still null
            LOGGER.info("Observation Empty");
        }
        optObservation.ifPresent(connection::sendObservationPacket);
    }

    void stop() { }

}
