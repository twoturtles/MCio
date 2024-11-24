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
public class MCioClientAsync {
    static MCioClientAsync instance;
    public static boolean windowFocused;

    private final Logger LOGGER = LogUtils.getLogger();
    private final MCioNetwork network;

    private final MinecraftClient client;
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
        instance = this;
        this.client = MinecraftClient.getInstance();
        network = new MCioNetwork(running);


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

    }

}

// Sends state updates to the agent
class StateHandler {
    private final MinecraftClient client;
    private final MCioClientAsync controller;
    private final AtomicBoolean running;
    // The ActionThread uses this to request a sequence number reset. E.g., after crashed agent.
    final AtomicBoolean doSequenceReset = new AtomicBoolean(false);
    private final SignalWithLatch signalHandler = new SignalWithLatch();

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond sendFPS = new TrackPerSecond("StatesSent");
    private int stateSequence = 0;
    private int ticks = 0;

    public StateHandler(MinecraftClient client, MCioClientAsync controller, ZContext zCtx,
                        int listen_port, AtomicBoolean running) {
        this.client = client;
        this.controller = controller;
        this.running = running;

        MCioFrameCapture.setEnabled(true);


        ClientTickEvents.START_CLIENT_TICK.register(client_cb -> {
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

        // XXX Use case? Maybe reset last action sequence to help a crashed agent?
        boolean doReset = doSequenceReset.getAndSet(false);
        if (doReset) {
            stateSequence = 0;
        }

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
                stateSequence++, this.controller.lastFullTickActionSequence,
                frameRV.frame_png, player.getHealth(),
                cursorMode, new int[] {cursorPosRV.x(), cursorPosRV.y()},
                fPlayerPos, player.getPitch(), player.getYaw(),
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand);
        LOGGER.debug("StatePacket: {}", statePkt);

        /* Send */
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
    private final MCioClientAsync controller;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond recvPPS = new TrackPerSecond("ActionsReceived");

    // Track keys and buttons that are currently pressed so we can clear them on reset.
    private final Set<Integer> keysPressed = new HashSet<>();
    private final Set<Integer> buttonsPressed = new HashSet<>();

    // Set at the end of processing an ActionPacket. Picked up by the State thread.
    public int lastSequenceProcessed = 0;

    /* XXX Clear all actions if remote controller disconnects? */
    public ActionHandler(MinecraftClient client, MCioClientAsync controller, ZContext zCtx,
                         int remote_port, AtomicBoolean running) {
        this.client = client;
        this.controller = controller;
        this.running = running;

    }

    private void processNextAction() {
        // Block waiting for next action.
        /* Recv */

        recvPPS.count();

        /* Reset handler */
        if (action.reset()) {
            for (int keyCode : keysPressed) {
                client.execute(() -> {
                    client.keyboard.onKey(client.getWindow().getHandle(),
                            keyCode, 0, GLFW.GLFW_RELEASE, 0);
                });
            }
            keysPressed.clear();

            for (int buttonCode : buttonsPressed) {
                client.execute(() -> {
                    client.keyboard.onKey(client.getWindow().getHandle(),
                            buttonCode, 0, GLFW.GLFW_RELEASE, 0);
                });
            }
            buttonsPressed.clear();

            this.controller.stateHandler.doSequenceReset.set(true);
        }

        /* Keyboard handler */
        for (int[] tuple : action.keys()) {
            int keyCode = tuple[0];
            int actionCode = tuple[1];
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        keyCode, 0, actionCode, 0);
            });
            if (actionCode == GLFW.GLFW_PRESS) {
                this.keysPressed.add(keyCode);
            } else if (actionCode == GLFW.GLFW_RELEASE) {
                this.keysPressed.remove(keyCode);
            }
        }

        /* Mouse handler */
        for (int[] tuple : action.mouse_buttons()) {
            int buttonCode = tuple[0];
            int actionCode = tuple[1];
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), buttonCode, actionCode, 0);
            });
            if (actionCode == GLFW.GLFW_PRESS) {
                this.buttonsPressed.add(buttonCode);
            } else if (actionCode == GLFW.GLFW_RELEASE) {
                this.buttonsPressed.remove(buttonCode);
            }
        }
        for (int[] tuple : action.mouse_pos()) {
            client.execute(() -> {
                ((MouseMixinInterface) client.mouse).onCursorPosAgent$Mixin(
                        client.getWindow().getHandle(), tuple[0], tuple[1]);
            });
        }

        lastSequenceProcessed = action.sequence();
    }

}

