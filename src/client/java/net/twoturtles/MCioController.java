package net.twoturtles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.client.util.Window;
import net.minecraft.client.Mouse;

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
 * - Fake cursor for menus. Or maybe send cursor position and let python do it.
 * - Other state
 * - step mode to allow stepping by ticks. Also allow above realtime speed.
 * - Disable idle frame slowdown?
 * - shared config file, override with env/command line option
 * - Command line args / config to start in paused state
 * - minerl compatible mode - find out other features to make it useful
 * - gymnasium
 * - tests - java and python
 */

/* Top-level class. Runs on client thread.
 * Spawns threads for receiving actions and sending state updates. */
public class MCioController {
    private final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT_ACTION = 5556;  // For receiving actions
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
    static StateHandler instance;   // XXX make sure only one
    private final MinecraftClient client;
    private final AtomicBoolean running;
    private final SignalWithLatch signalHandler = new SignalWithLatch();

    private final ZMQ.Socket stateSocket;
    private final Thread stateThread;
    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackFPS sendFPS = new TrackFPS("SEND");

    public int lastActionPosX = 0;
    public int lastActionPosY = 0;

    public StateHandler(MinecraftClient client, ZContext zCtx, int listen_port, AtomicBoolean running) {
        instance = this;
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
                signalHandler.waitForSignal();
                sendNextState();
            }
        } finally {
            cleanupSocket();
        }
    }

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
        getCursorPosRV posRV = getCursorPos(client);

        int x = (int) ((MouseMixin.MouseAccessor) client.mouse).getX();
        int y = (int) ((MouseMixin.MouseAccessor) client.mouse).getY();

        LOGGER.warn("");
        LOGGER.warn("posMouse {},{}", x, y);
        LOGGER.warn("posGL {},{}", posRV.x(), posRV.y());
        LOGGER.warn("posAction {},{}", lastActionPosX, lastActionPosY);

        int cursorMode = GLFW.glfwGetInputMode(window.getHandle(), GLFW.GLFW_CURSOR);
        // There are other modes, but I believe these are the two used by Minecraft.
        cursorMode = cursorMode == GLFW.GLFW_CURSOR_DISABLED ? cursorMode : GLFW.GLFW_CURSOR_NORMAL;

        /* Create packet */
        StatePacket statePkt = new StatePacket(NetworkDefines.MCIO_PROTOCOL_VERSION,
                frameRV.frame_count(), frameRV.frame_png, player.getHealth(),
                cursorMode, new int[] {x, y},
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand);

        /* Send */
        try {
            byte[] pBytes = StatePacketPacker.pack(statePkt);
            stateSocket.send(pBytes);
        } catch (IOException e) {
            LOGGER.warn("StatePacketPacker failed");
        }

        // TODO
        // damage?
        // Coordinates
        // Direction
        // Experience
        // Enchantments
        // Status effects
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
        double[] dReadX = new double[1];
        double[] dReadY = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), dReadX, dReadY);
        double mouseX = dReadX[0];
        double mouseY = dReadY[0];

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
    static ActionHandler instance;   // XXX make sure only one
    private final MinecraftClient client;
    private final AtomicBoolean running;

    private final ZMQ.Socket actionSocket;
    private final Thread actionThread;
    private final Logger LOGGER = LogUtils.getLogger();

    /* XXX Clear all actions if remote controller disconnects? */
    public ActionHandler(MinecraftClient client, ZContext zCtx, int remote_port, AtomicBoolean running) {
        instance = this;
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
        LOGGER.info("ACTION {}", action);

        /* Keyboard handlers */
        for (int key : action.keys_pressed()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int key : action.keys_released()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_RELEASE, 0);
            });
        }

        /* Mouse handlers */
        if (action.mouse_pos_update()) {
            StateHandler.instance.lastActionPosX = action.mouse_pos_x();
            StateHandler.instance.lastActionPosY = action.mouse_pos_y();

            client.execute(() -> {
                ((MouseMixinInterface) client.mouse).onCursorPosAgent(client.getWindow().getHandle(),
                        action.mouse_pos_x(), action.mouse_pos_y());
            });
        }
        for (int button : action.mouse_buttons_pressed()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int button : action.mouse_buttons_released()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_RELEASE, 0);
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

