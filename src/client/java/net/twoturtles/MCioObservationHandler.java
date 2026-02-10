package net.twoturtles;

import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

// Collect information to send to the agent
// XXX Use static packet fields to reduce memory operations?
public class MCioObservationHandler {
  private final Minecraft client;
  private final MCioConfig config;

  private final Logger LOGGER = LogUtils.getLogger();
  private static final TrackPerSecond sendFPS = new TrackPerSecond("ObservationsSent");
  private int observationSequence = 0;

  public MCioObservationHandler(Minecraft client, MCioConfig config) {
    this.client = client;
    this.config = config;
  }

  // TODO - more things in the observation packet
  // Experience
  // Enchantments
  // Status effects

  // Collect observation and package into an ObservationPacket
  Optional<ObservationPacket> collectObservation(int lastFullTickActionSequence) {
    LocalPlayer player = client.player;
    if (player == null) {
      return Optional.empty();
    }

    ArrayList<Option> options = new ArrayList<>();

    /* Gather information */
    FrameRV frameRV = getFrame();
    InventoriesRV inventoriesRV = getInventories();

    // XXX For now just manually add the stats update.
    // This will be based on the action packet in the future.
    options.add(getStatsUpdate());
    //        options.add(getStatsFull());

    getCursorPosRV cursorPosRV = getCursorPos(client);

    Vec3 playerPos = player.position();
    float[] fPlayerPos =
        new float[] {(float) playerPos.x, (float) playerPos.y, (float) playerPos.z};

    Window window = client.getWindow();
    int cursorMode = GLFW.glfwGetInputMode(window.getWindow(), GLFW.GLFW_CURSOR);
    // There are other modes, but I believe these are the two used by Minecraft.
    cursorMode = cursorMode == GLFW.GLFW_CURSOR_DISABLED ? cursorMode : GLFW.GLFW_CURSOR_NORMAL;

    /* Create packet */
    ObservationPacket observationPkt =
        new ObservationPacket(
            MCioConfig.MCIO_PROTOCOL_VERSION,
            observationSequence++,
            config.mode.toString(),
            lastFullTickActionSequence,
            frameRV.sequence,
            frameRV.height,
            frameRV.width,
            frameRV.type.toString(),
            frameRV.frame,
            cursorMode,
            new double[] {cursorPosRV.x, cursorPosRV.y},
            player.getHealth(),
            fPlayerPos,
            player.getXRot(),
            getYaw(player),
            inventoriesRV.main,
            inventoriesRV.armor,
            inventoriesRV.offHand,
            options);
    LOGGER.debug("ObservationPacket: {}", observationPkt);

    return Optional.of(observationPkt);
  }

  /*
   * Methods for collecting observation data from Minecraft
   */

  StatsUpdateOption getStatsUpdate() {
    Map<String, ArrayList<Stat>> grouped = new HashMap<>();
    MCioStats.getInstance()
        .takePendingStats(
            true,
            (category, id, value) -> {
              grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(new Stat(id, value));
            });
    ArrayList<StatCategory> updates = new ArrayList<>();
    for (Map.Entry<String, ArrayList<Stat>> entry : grouped.entrySet()) {
      updates.add(new StatCategory(entry.getKey(), entry.getValue()));
    }
    return new StatsUpdateOption(updates);
  }

  StatsFullOption getStatsFull() {
    Map<String, ArrayList<Stat>> grouped = new HashMap<>();
    MCioStats.getInstance()
        .statsForEach(
            (category, id, value) -> {
              grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(new Stat(id, value));
            });
    ArrayList<StatCategory> updates = new ArrayList<>();
    for (Map.Entry<String, ArrayList<Stat>> entry : grouped.entrySet()) {
      updates.add(new StatCategory(entry.getKey(), entry.getValue()));
    }
    return new StatsFullOption(updates);
  }

  float getYaw(LocalPlayer player) {
    float yaw = player.getYRot();
    // Normalize yaw -180 to 180. Minecraft already normalizes pitch -90 to 90.
    yaw = yaw % 360f;
    if (yaw > 180f) {
      yaw -= 360f;
    }
    return yaw;
  }

  /* Return type for getFrame */
  record FrameRV(
      int sequence, int height, int width, MCioConfig.MCioFrameType type, ByteBuffer frame) {
    public static FrameRV empty() {
      return new FrameRV(
          0, // Maybe make this -1 to signify empty
          0,
          0,
          MCioConfig.DEFAULT_MCIO_FRAME_TYPE,
          ByteBuffer.allocate(0) // empty ByteBuffer
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
    MCioConfig config = MCioConfig.getInstance();
    ByteBuffer frameBuf =
        switch (config.frameType) {
          case RAW -> MCioFrameCapture.getInstance().getFrameRAW(frame);
        };
    return new FrameRV(
        frame.frame_sequence(), frame.height(), frame.width(), config.frameType, frameBuf);
  }

  /* Return type for getInventories() */
  record InventoriesRV(
      ArrayList<InventorySlot> main,
      ArrayList<InventorySlot> armor,
      // Even though it's only one item, use array for consistency.
      ArrayList<InventorySlot> offHand) {
    public static InventoriesRV empty() {
      return new InventoriesRV(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  private InventoriesRV getInventories() {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) {
      return InventoriesRV.empty();
    }

    Inventory inventory = player.getInventory();
    // main includes hotBar (0-8) and regular inventory (9-35). Split these?
    ArrayList<InventorySlot> main = readInventory(inventory.items);
    ArrayList<InventorySlot> armor = readInventory(inventory.armor);
    ArrayList<InventorySlot> offHand = readInventory(inventory.offhand);
    return new InventoriesRV(main, armor, offHand);
  }

  private ArrayList<InventorySlot> readInventory(List<ItemStack> inventoryList) {
    ArrayList<InventorySlot> slots = new ArrayList<>();
    for (int slot_num = 0; slot_num < inventoryList.size(); slot_num++) {
      ItemStack stack = inventoryList.get(slot_num);
      if (!stack.isEmpty()) {
        InventorySlot inventorySlot =
            new InventorySlot(
                slot_num,
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount());
        slots.add(inventorySlot);
      }
    }
    return slots;
  }

  record getCursorPosRV(double x, double y) {}

  private getCursorPosRV getCursorPos(Minecraft client) {
    Window window = client.getWindow();
    if (window == null) {
      return new getCursorPosRV(0.0, 0.0);
    }

    // Scale mouse position to frame.
    // This only matters for high DPI displays (Retina), but doing this works either way.
    double scaleX = (double) window.getWidth() / window.getScreenWidth();
    double scaleY = (double) window.getHeight() / window.getScreenHeight();
    // Mouse positions are relative to the window.
    double frameMouseX = client.mouseHandler.xpos() * scaleX;
    double frameMouseY = client.mouseHandler.ypos() * scaleY;

    return new getCursorPosRV(frameMouseX, frameMouseY);
  }
}
