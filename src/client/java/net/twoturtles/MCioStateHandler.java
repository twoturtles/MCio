package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import net.twoturtles.mixin.client.MouseMixin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.zeromq.ZContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/*
        // StateHandler sends a signal on END_CLIENT_TICK
        signalHandler.waitForSignal();
        sendNextState();
 */

// Collect state information to send to the agent
// All information is client side?
public class MCioStateHandler {
    private final MinecraftClient client;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond sendFPS = new TrackPerSecond("StatesSent");
    private int stateSequence = 0;

    public MCioStateHandler(MinecraftClient client) {
        this.client = client;
    }

    // TODO - more things in the state packet
    // Experience
    // Enchantments
    // Status effects

    // Collect state and package into a StatePacket
    Optional<StatePacket> collectState() {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return Optional.empty();
        }

        // XXX Use case? Maybe reset last action sequence to help a crashed agent?
//        boolean doReset = doSequenceReset.getAndSet(false);
//        if (doReset) {
//            stateSequence = 0;
//        }

        /* Gather information */
        FrameRV frameRV = getFrame();
        InventoriesRV inventoriesRV = getInventories();
        getCursorPosRV cursorPosRV = getCursorPos(client);

        Vec3d playerPos =  player.getPos();
        float[] fPlayerPos = new float[] {(float) playerPos.x, (float) playerPos.y, (float) playerPos.z};

        Window window = client.getWindow();
        int cursorMode = GLFW.glfwGetInputMode(window.getHandle(), GLFW.GLFW_CURSOR);
        // There are other modes, but I believe these are the two used by Minecraft.
        cursorMode = cursorMode == GLFW.GLFW_CURSOR_DISABLED ? cursorMode : GLFW.GLFW_CURSOR_NORMAL;

        /* Create packet */
        StatePacket statePkt = new StatePacket(NetworkDefines.MCIO_PROTOCOL_VERSION,
                stateSequence++, 0/* XXX this.controller.lastFullTickActionSequence */,
                frameRV.frame_png, player.getHealth(),
                cursorMode, new int[] {cursorPosRV.x(), cursorPosRV.y()},
                fPlayerPos, player.getPitch(), player.getYaw(),
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand);
        LOGGER.debug("StatePacket: {}", statePkt);

        return Optional.of(statePkt);
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
