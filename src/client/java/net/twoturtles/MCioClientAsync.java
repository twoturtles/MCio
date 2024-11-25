package net.twoturtles;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MCioClientAsync {
    private final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftClient client;

    private final MCioNetworkConnection connection;
    private final MCioActionHandler actionHandler;
    private final MCioObservationHandler observationHandler;

    private final AtomicBoolean running = new AtomicBoolean(true);

    // This tracks the last action sequence that has been processed before a full client tick.
    // XXX This is an attempt to determine the last action that was processed by the server. It is
    // passed back to the agent in observation packets so it can determine when it has received an observation that
    // has been updated by the last action. I don't really know when actions at the client have been
    // sent to the server and its observation has been updated. Maybe we could look at server ticks, but
    // how would that work with multiplayer servers? Should observations be sent from the server? But then
    // how do we send frames? May need to separate the two observation sources at some point. That will probably
    // be necessary for multiplayer anyway.
    private int actionSequenceLastReceived = 0;    // XXX not synchronized
    private int actionSequenceAtTickStart = 0;
    private int lastFullTickActionSequence = 0;

    // Observations are sent at the end of every client tick. Actions are received and processed on
    // a separate thread.
    public MCioClientAsync(MCioConfig config) {
        client = MinecraftClient.getInstance();

        connection = new MCioNetworkConnection();
        actionHandler = new MCioActionHandler(client);
        observationHandler = new MCioObservationHandler(client, config);

        Thread actionThread = new Thread(this::actionThreadRun, "MCio-ActionThread");
        LOGGER.info("Process-Action-Thread start");
        actionThread.start();

        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
            actionSequenceAtTickStart = actionSequenceLastReceived;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            // Synchronization - loading lastSequenceProcessed into local
            int newActionSequence = actionSequenceLastReceived;
            if (newActionSequence >= actionSequenceAtTickStart) {
                lastFullTickActionSequence = newActionSequence;
            }
        });

        /* Send observation at the end of every tick */
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            Optional<ObservationPacket> opt = observationHandler.collectObservation(lastFullTickActionSequence);
            opt.ifPresent(connection::sendObservationPacket);
        });
    }

    // Receive and process actions. Separate thread since it will block waiting for an action.
    private void actionThreadRun() {
        while (running.get()) {
            Optional<ActionPacket> opt = connection.recvActionPacket();
            if (opt.isPresent()) {
                ActionPacket action = opt.get();
                actionHandler.processAction(action);
                actionSequenceLastReceived = action.sequence();
            }
        }
    }

    public void stop() {
        running.set(false);
        connection.close();
    }
}

