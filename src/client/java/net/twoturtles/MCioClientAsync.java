package net.twoturtles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.client.util.Window;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.lwjgl.glfw.GLFW;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import net.twoturtles.mixin.client.MouseMixin;

public class MCioClientAsync {
    public static boolean windowFocused;
    private final MinecraftClient client;
    private final MCioActionHandler actionHandler;
    private final MCioStateHandler stateHandler;

    private final Logger LOGGER = LogUtils.getLogger();
    private final MCioNetwork network;

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
        network = new MCioNetwork(running);
        actionHandler = new MCioActionHandler(client);
        stateHandler = new MCioStateHandler(client);

        Thread actionThread = new Thread(this::actionThreadRun, "MCio-ProcessActionThread");
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
            /* This will run on the client thread. When the tick ends, signal the state thread to send an update.
             * The server state is only updated once per tick so it makes the most sense to send an update
             * after that.
             * XXX Not sure how SERVER_TICK corresponds to CLIENT_TICK */
            signalHandler.sendSignal();
            this.ticks++;
        });
    }

    private void actionThreadRun() {
        try {
            while (running.get()) {
            }
        } finally {
            cleanupSocket();
        }
    }

/* Used to signal between the render thread capturing frames and the state thread sending
     * frames and state to the agent. */
    class SignalWithLatch {
        private CountDownLatch latch = new CountDownLatch(1);
        public void waitForSignal() {
            try {
                /* Waits until the latch goes to 0. */
                latch.await();
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted");
            }
            latch = new CountDownLatch(1);  // Reset for next use
        }
        public void sendSignal() {
            latch.countDown();
        }
    }

}

