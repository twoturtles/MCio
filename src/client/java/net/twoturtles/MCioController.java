package net.twoturtles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;


import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import net.twoturtles.mixin.client.MouseMixin;
import net.twoturtles.util.TrackFPS;

/* TODO
 * - Ensure all calls to random come from the same seed?
 * - Fake cursor for menus. Or maybe send cursor position and let python do it.
 * - Other state
 * - step mode to allow stepping by ticks. Also allow above realtime speed.
 * - Disable idle frame slowdown?
 * - shared config file, override with env/command line option
 * - Command line args / config to start in paused state
 * - minerl compatible mode - find out other features to make it useful
 * - gymnasium
 */

/* Top-level class. Runs on client thread.
 * Spawns threads for receiving commands and sending state updates. */
public class MCioController {
    private final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT_CMD = 5556;  // For receiving commands
    private static final int PORT_STATE = 5557;    // For sending screen and other state.
    private final ZContext context;

    private final MinecraftClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MCioController() {
        this.context = new ZContext();

        this.client = MinecraftClient.getInstance();
    }

    public void start() {
        this.running.set(true);

        // Start threads
        CommandHandler cmd = new CommandHandler(client, context, PORT_CMD, running);
        cmd.start();

        StateHandler state = new StateHandler(client, context, PORT_STATE, running);
        state.start();
    }

    public void stop() {
        running.set(false);
        if (context != null) {
            context.close();
        }
    }
}

class StateHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;
    private final SignalWithLatch signalHandler = new SignalWithLatch();

    private final ZMQ.Socket stateSocket;
    private final Thread stateThread;
    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackFPS sendFPS = new TrackFPS("SEND");

    public StateHandler(MinecraftClient client, ZContext zCtx, int listen_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        MCioFrameCapture.setEnabled(true);

        stateSocket = zCtx.createSocket(SocketType.PUB);  // Pub for sending state
        stateSocket.bind("tcp://*:" + listen_port);

        this.stateThread = new Thread(this::stateThreadRun, "MCio-StateThread");

        /* Send state at the end of every tick */
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
             /* This will run on the client thread. When the tick ends, signal the state thread to send an update.
              * The server state is only updated once per tick so it makes the most sense to send an update
              * after that. */
            /* XXX Should it be the server that sends the signal? */
            signalHandler.sendSignal();
        });
    }

    public void start() {
        LOGGER.warn("Thread start");
        stateThread.start();
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

    private void stateThreadRun() {
        try {
            while (running.get()) {
                signalHandler.waitForSignal();
                sendNextState();
            }
        } finally {
            cleanupSocket();
        }
    }

    private void sendNextState() {
        FrameRV frameRV = getFrame();
        InventoriesRV inventoriesRV = getInventories();

        StatePacket statePkt = new StatePacket(frameRV.frame_count(), frameRV.frame_png,
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand, "Hello..");

        try {
            byte[] pBytes = StatePacketPacker.pack(statePkt);
            stateSocket.send(pBytes);
        } catch (IOException e) {
            LOGGER.warn("StatePacketPacker failed");
        }

        // TODO
        // float health = player.getHealth();
        // import net.minecraft.entity.damage.DamageSource;
        // Coordinates
        // Direction
        // Experience
        // Enchantments
        // Status effects
    }

    /* Return type for getFrame */
    record FrameRV(
            int frame_count,
            ByteBuffer frame_png
    ){
        public static FrameRV empty() {
            return new FrameRV(
                    0,  // Maybe make this -1 to signify empty
                    ByteBuffer.allocate(0)  // empty ByteBuffer
            );
        }
    }
    private FrameRV getFrame() {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getLastCapturedFrame();
        if (frame == null || frame.frame() == null) {
            return FrameRV.empty();
        }

        /* If FPS SEND > FPS CAPTURE, we'll be sending duplicate frames. */
        if (sendFPS.count()) {
            LOGGER.warn("SEND FRAME {}", frame.frame_count());
        }
        ByteBuffer pngBuf = MCioFrameCapture.getFramePNG(frame);
        return new FrameRV(frame.frame_count(), pngBuf);
    }

    /* Return type for getInventoriesRV() */
    record InventoriesRV(
            ArrayList<InventorySlot> main,
            ArrayList<InventorySlot> armor,
            // Even though it's only one item, use array for consistency.
            ArrayList<InventorySlot> offHand
    ) {
        public static InventoriesRV empty() {
            return new InventoriesRV(
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }
    }
    private InventoriesRV getInventories() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return InventoriesRV.empty();
        }

        PlayerInventory inventory = player.getInventory();
        // main includes hotBar (0-8) and regular inventory (9-35). Split these?
        ArrayList<InventorySlot> main = readInventory(inventory.main);
        ArrayList<InventorySlot> armor = readInventory(inventory.armor);
        ArrayList<InventorySlot> offHand = readInventory(inventory.offHand);
        return new InventoriesRV(main, armor, offHand);
    }

    private ArrayList<InventorySlot> readInventory(List<ItemStack> inventoryList) {
        ArrayList<InventorySlot> slots = new ArrayList<>();
        for (int slot_num = 0; slot_num < inventoryList.size(); slot_num++) {
            ItemStack stack = inventoryList.get(slot_num);
            if (!stack.isEmpty()) {
                InventorySlot inventorySlot = new InventorySlot(
                        slot_num, Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()
                );
                slots.add(inventorySlot);
            }
        }
        return slots;
    }

    private void cleanupSocket() {
        LOGGER.info("State thread cleanup");
        if (stateSocket != null) {
            try {
                stateSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing state socket", e);
            }
        }
    }
}

/* Handles incoming commands and passes to the client/render thread. Runs on own thread. */
class CommandHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;

    private final ZMQ.Socket cmdSocket;
    private final Thread cmdThread;
    private final Logger LOGGER = LogUtils.getLogger();

    /* XXX Clear all commands if remote controller disconnects? */
    public CommandHandler(MinecraftClient client, ZContext zCtx, int remote_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        cmdSocket = zCtx.createSocket(SocketType.SUB);  // Sub socket for receiving commands
        cmdSocket.connect("tcp://localhost:" + remote_port);
        cmdSocket.subscribe(new byte[0]); // Subscribe to everything

        this.cmdThread = new Thread(this::commandThreadRun, "MCio-CommandThread");
    }

    public void start() {
        cmdThread.start();
    }

    private void commandThreadRun() {
        try {
            while (running.get()) {
                try {
                    processNextCommand();
                } catch (ZMQException e) {
                    handleZMQException(e);
                    break;
                }
            }
        } finally {
            cleanupSocket();
        }
    }

    private void processNextCommand() {
        // Block waiting for next command.
        byte[] pkt = cmdSocket.recv();
        Optional<CmdPacket> packetOpt = CmdPacketUnpacker.unpack(pkt);
        if (packetOpt.isEmpty()) {
            LOGGER.warn("Received invalid command packet");
            return;
        }

        CmdPacket cmd = packetOpt.get();
        LOGGER.info("CMD {}", cmd);

        /* Keyboard handlers */
        for (int key : cmd.keys_pressed()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int key : cmd.keys_released()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_RELEASE, 0);
            });
        }

        /* Mouse handlers */
        if (cmd.mouse_pos_update()) {
            client.execute(() -> {
                ((MouseMixin.OnCursorPosInvoker) client.mouse).invokeOnCursorPos(
                        client.getWindow().getHandle(), cmd.mouse_pos_x(), cmd.mouse_pos_y());
            });
        }
        for (int button : cmd.mouse_buttons_pressed()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int button : cmd.mouse_buttons_released()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_RELEASE, 0);
            });
        }
    }

    private void handleZMQException(ZMQException e) {
        if (!running.get()) {
            LOGGER.info("Command thread shutting down");
        } else {
            LOGGER.error("ZMQ error in command thread", e);
        }
    }

    private void cleanupSocket() {
        LOGGER.info("Command thread cleanup");
        if (cmdSocket != null) {
            try {
                cmdSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing command socket", e);
            }
        }
    }
}

