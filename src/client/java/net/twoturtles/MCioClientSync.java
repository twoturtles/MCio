package net.twoturtles;

import java.util.Optional;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MCioClientSync {
    private final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftClient client;
    private MCioConfig config;

    private final MCioNetworkConnection connection;
    private final MCioActionHandler actionHandler;
    private final MCioObservationHandler observationHandler;
    private final MCioSyncUtil syncUtil = MCioSyncUtil.getInstance();

    private boolean waitingForFirstAction = true;
    private int lastActionSequence = 0;
    private int ticks = 0;

    /**
     * See MCioServerSync for more info.
     */
    MCioClientSync(MCioConfig config) {
        client = MinecraftClient.getInstance();
        this.config = config;

        connection = new MCioNetworkConnection();
        actionHandler = new MCioActionHandler(client);
        observationHandler = new MCioObservationHandler(client, config);

        connection.registerSocketStateCallback(this::socketStateCallback);

        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
            ticks++;
            MCioClientSyncUtil.checkAndSetGameRunning();
            syncUtil.clientStartTick();
            if (syncUtil.isGameRunning()) {
                processAction();
            }
        });

        MCioFrameCapture frameCapture = MCioFrameCapture.getInstance();
        frameCapture.registerCaptureCallback(frame -> {
            if (syncUtil.isGameRunning()) {
                // Capture happens just before swapBuffers. Client ticks happen before the render.
                // So this happens after the end of the client tick.
                generateObservation();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            syncUtil.clientEndTick();
        });

        // For testing
        if (config.syncSpeedTest) {
            new SpeedTest();
        }
    }

    void processAction() {
        // XXX Make window responsive while waiting for an action. At least allow it to be brought to the foreground.
        // XXX Hangs if you go to the menu

        if (waitingForFirstAction) {
            LOGGER.info("Waiting for first action");
        }
        Optional<ActionPacket> optAction = connection.recvActionPacket(true);
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
        LOGGER.debug("ACTION {}", action);
        actionHandler.processAction(action);
    }

    // XXX Ideally this would include the update from the server
    void generateObservation() {
        Optional<ObservationPacket> opt = observationHandler.collectObservation(lastActionSequence);
        if (opt.isPresent()) {
            connection.sendObservationPacket(opt.get(), false);
        } else {
            // client.player is still null
            LOGGER.info("Observation Empty");
        }
    }

    /**
     * If the Action connection goes down, automatically clear inputs.
     */
    private void socketStateCallback(MCioNetworkConnection.MCioSocketType type, boolean connected) {
        if (type == MCioNetworkConnection.MCioSocketType.ACTION && !connected) {
            LOGGER.info("Clearing Input (Disconnect)");
            actionHandler.clearInput();
        }
    }

    void stop() {
        connection.close();
    }

}
