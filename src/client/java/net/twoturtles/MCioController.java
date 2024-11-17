package net.twoturtles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
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
import net.twoturtles.util.TrackFPS;

/* TODO
 * - Ensure all calls to random come from the same seed?
 * - step mode to allow stepping by ticks. Also allow above realtime speed.
 * - Disable idle frame slowdown?
 *      client.getInactivityFpsLimiter()
 * - shared config file, override with env/command line option
 * - separate logging with level config
 * - Command line args / config to start in paused state
 * - minerl compatible mode - find out other features to make it useful
 * - gymnasium
 * - tests - java and python
 * - Save and replay scripts
 * - Asynchronous and synchronous modes
 * - Everything in client, so server could be run separately
 * - Bind both sockets in minecraft? Would this fix zmq slow joiner?
 */

/* Top-level class. Starts on the client thread.
 * Spawns threads for receiving actions and sending state updates. */
public class MCioController {
    static MCioController instance;
    public static boolean windowFocused;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT_ACTION = 5556;  // For receiving actions
    private static final int PORT_STATE = 5557;    // For sending screen and other state.
    private final ZContext context;

    private final MinecraftClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);


    public MCioController() {
        instance = this;
        this.context = new ZContext();
        this.client = MinecraftClient.getInstance();
    }

    public void start() {
        this.running.set(true);

        // Start threads
        ActionHandler action = new ActionHandler(client, context, PORT_ACTION, running);
        action.start();

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
    private int sequence = 0;
    private int ticks = 0;

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
            signalHandler.sendSignal();
            this.ticks++;
        });
    }

    public void start() {
        LOGGER.warn("Thread start");
        stateThread.start();
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
                // StateHandler sends a signal on END_CLIENT_TICK
                signalHandler.waitForSignal();
                sendNextState();
            }
        } finally {
            cleanupSocket();
        }
    }

    // TODO - more things in the state packet
    // Experience
    // Enchantments
    // Status effects

    /* Send state to agent */
    private void sendNextState() {
        MinecraftClient mcClient = MinecraftClient.getInstance();
        ClientPlayerEntity player = mcClient.player;
        if (player == null) {
            return;
        }
        Window window = client.getWindow();

        /* Gather information */
        FrameRV frameRV = getFrame();
        InventoriesRV inventoriesRV = getInventories();
        getCursorPosRV cursorPosRV = getCursorPos(client);

        Vec3d playerPos =  player.getPos();
        float[] fPlayerPos = new float[] {(float) playerPos.x, (float) playerPos.y, (float) playerPos.z};

        int cursorMode = GLFW.glfwGetInputMode(window.getHandle(), GLFW.GLFW_CURSOR);
        // There are other modes, but I believe these are the two used by Minecraft.
        cursorMode = cursorMode == GLFW.GLFW_CURSOR_DISABLED ? cursorMode : GLFW.GLFW_CURSOR_NORMAL;

        /* Create packet */
        StatePacket statePkt = new StatePacket(NetworkDefines.MCIO_PROTOCOL_VERSION,
                sequence++, 0 /* XXX */,
                frameRV.frame_png, player.getHealth(),
                cursorMode, new int[] {cursorPosRV.x(), cursorPosRV.y()},
                fPlayerPos, player.getPitch(), player.getYaw(),
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand);

        /* Send */
        try {
            byte[] pBytes = StatePacketPacker.pack(statePkt);
            stateSocket.send(pBytes);
        } catch (IOException e) {
            LOGGER.warn("StatePacketPacker failed");
        }
    }

    /*
     * Methods for collecting state data from Minecraft
     */

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
        sendFPS.count();
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

    record getCursorPosRV(
            int x,
            int y
    ){}
    private getCursorPosRV getCursorPos(MinecraftClient client) {
        Window window = client.getWindow();
        if (window == null) {
            return new getCursorPosRV(0, 0);
        }

        // Mouse position - these are relative to the window.
        int mouseX = (int) ((MouseMixin.MouseAccessor) client.mouse).getX();
        int mouseY = (int) ((MouseMixin.MouseAccessor) client.mouse).getY();

        // Scale mouse position to frame.
        // This only matters for high DPI displays (Retina), but doing this works either way.
        long winWidth = window.getWidth();
        long winHeight = window.getHeight();
        int winFrameWidth = window.getFramebufferWidth();
        int winFrameHeight = window.getFramebufferHeight();
        int frameMouseX = (int) (mouseX * (double)winFrameWidth / winWidth);
        int frameMouseY = (int) (mouseY * (double)winFrameHeight / winHeight);

        return new getCursorPosRV(frameMouseX, frameMouseY);
    }
}

/* Handles incoming actions and passes to the client/render thread. Runs on own thread. */
class ActionHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;

    private final ZMQ.Socket actionSocket;
    private final Thread actionThread;
    private final Logger LOGGER = LogUtils.getLogger();

    /* XXX Clear all actions if remote controller disconnects? */
    public ActionHandler(MinecraftClient client, ZContext zCtx, int remote_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        actionSocket = zCtx.createSocket(SocketType.SUB);  // Sub socket for receiving actions
        actionSocket.connect("tcp://localhost:" + remote_port);
        actionSocket.subscribe(new byte[0]); // Subscribe to everything

        this.actionThread = new Thread(this::actionThreadRun, "MCio-ActionThread");
    }

    public void start() {
        actionThread.start();
    }

    private void actionThreadRun() {
        try {
            while (running.get()) {
                try {
                    processNextAction();
                } catch (ZMQException e) {
                    handleZMQException(e);
                    break;
                }
            }
        } finally {
            cleanupSocket();
        }
    }

    private void processNextAction() {
        // Block waiting for next action.
        byte[] pkt = actionSocket.recv();
        Optional<ActionPacket> packetOpt = ActionPacketUnpacker.unpack(pkt);
        if (packetOpt.isEmpty()) {
            LOGGER.warn("Received invalid action packet");
            return;
        }

        ActionPacket action = packetOpt.get();
        //LOGGER.info("ACTION {} {}", action, action.arrayToString(action.mouse_pos()));

        /* Keyboard handler */
        for (int[] tuple : action.keys()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        tuple[0], 0, tuple[1], 0);
            });
        }

        /* Mouse handler */
        for (int[] tuple : action.mouse_buttons()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), tuple[0], tuple[1], 0);
            });
        }
        for (int[] tuple : action.mouse_pos()) {
            client.execute(() -> {
                ((MouseMixinInterface) client.mouse).onCursorPosAgent$Mixin(
                        client.getWindow().getHandle(), tuple[0], tuple[1]);
            });
        }

    }

    private void handleZMQException(ZMQException e) {
        if (!running.get()) {
            LOGGER.info("Action thread shutting down");
        } else {
            LOGGER.error("ZMQ error in action thread", e);
        }
    }

    private void cleanupSocket() {
        LOGGER.info("Action thread cleanup");
        if (actionSocket != null) {
            try {
                actionSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing action socket", e);
            }
        }
    }
}

