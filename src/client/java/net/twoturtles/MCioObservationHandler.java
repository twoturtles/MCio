package net.twoturtles;

import com.mojang.logging.LogUtils;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// Collect information to send to the agent
// All information is client side?
public class MCioObservationHandler {
    private final MinecraftClient client;
    private final MCioConfig config;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond sendFPS = new TrackPerSecond("ObservationsSent");
    private int observationSequence = 0;

    public MCioObservationHandler(MinecraftClient client, MCioConfig config) {
        this.client = client;
        this.config = config;
    }

    // TODO - more things in the observation packet
    // Experience
    // Enchantments
    // Status effects

    // Collect observation and package into an ObservationPacket
    Optional<ObservationPacket> collectObservation(int lastFullTickActionSequence) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return Optional.empty();
        }

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
        ObservationPacket observationPkt = new ObservationPacket(NetworkDefines.MCIO_PROTOCOL_VERSION,
                config.mode.toString(), observationSequence++, lastFullTickActionSequence, frameRV.frame_sequence(),

                frameRV.frame_png, player.getHealth(),
                cursorMode, new int[] {cursorPosRV.x(), cursorPosRV.y()},
                fPlayerPos, player.getPitch(), getYaw(player),
                inventoriesRV.main, inventoriesRV.armor, inventoriesRV.offHand);
        LOGGER.debug("ObservationPacket: {}", observationPkt);

        return Optional.of(observationPkt);
    }

    /*
     * Methods for collecting observation data from Minecraft
     */

    private float getYaw(ClientPlayerEntity player) {
        float yaw = player.getYaw();
        // Normalize yaw -180 to 180. Minecraft already normalizes pitch -90 to 90.
        yaw = yaw % 360f;
        if (yaw > 180f) {
            yaw -= 360f;
        }
        return yaw;
    }

    /* Return type for getFrame */
    record FrameRV(
            int frame_sequence,
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
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getInstance().getLastCapturedFrame();
        if (frame == null || frame.frame() == null) {
            return FrameRV.empty();
        }

        /* If FPS SEND > FPS CAPTURE, we'll be sending duplicate frames. */
        sendFPS.count();
        ByteBuffer pngBuf = MCioFrameCapture.getInstance().getFramePNG(frame);
        return new FrameRV(frame.frame_sequence(), pngBuf);
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
