package net.twoturtles;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MCioClientAsync {
    public static boolean windowFocused;
    private final MinecraftClient client;
    private final MCioActionHandler actionHandler;
    private final MCioStateHandler stateHandler;

    private final Logger LOGGER = LogUtils.getLogger();
    private final MCioNetworkConnection connection;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private int actionSequenceAtTickStart = 0;
    // This tracks the last action sequence that has been processed before a full client tick.
    // XXX This is an attempt to determine the last action that was processed by the server. It is
    // passed back to the agent in state packets so it can determine when it has received state that
    // has been updated by the last action. I don't really know when actions at the client have been
    // sent to the server and its state has been updated. Maybe we could look at server ticks, but
    // how would that work with multiplayer servers? Should state be sent from the server? But then
    // how do we send frames? May need to separate the two state sources at some point. That will probably
    // be necessary for multiplayer anyway.
    public int lastFullTickActionSequence = 0;

    public MCioClientAsync() {
        client = MinecraftClient.getInstance();
        connection = new MCioNetworkConnection();
        actionHandler = new MCioActionHandler(client);
        stateHandler = new MCioStateHandler(client);

        Thread actionThread = new Thread(this::actionThreadRun, "MCio-ActionThread");
        LOGGER.info("Process-Action-Thread start");
        actionThread.start();



        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
            actionSequenceAtTickStart = this.actionHandler.lastSequenceProcessed;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            // Synchronization - loading lastSequenceProcessed into local
            int newActionSequence = this.actionHandler.lastSequenceProcessed;
            if (newActionSequence >= actionSequenceAtTickStart) {
                lastFullTickActionSequence = newActionSequence;
            }
        });

        /* Send state at the end of every tick */
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            Optional<StatePacket> opt = stateHandler.collectState();
            opt.ifPresent(connection::sendStatePacket);
        });
    }

    // Receive and process actions. Separate thread since it will block waiting for an action.
    private void actionThreadRun() {
        while (running.get()) {
            Optional<ActionPacket> opt = connection.recvActionPacket();
            opt.ifPresent(actionHandler::processAction);
        }
    }
//
///* Used to signal between the render thread capturing frames and the state thread sending
//     * frames and state to the agent. */
//    class SignalWithLatch {
//        private CountDownLatch latch = new CountDownLatch(1);
//        public void waitForSignal() {
//            try {
//                /* Waits until the latch goes to 0. */
//                latch.await();
//            } catch (InterruptedException e) {
//                LOGGER.warn("Interrupted");
//            }
//            latch = new CountDownLatch(1);  // Reset for next use
//        }
//        public void sendSignal() {
//            latch.countDown();
//        }
//    }

}

