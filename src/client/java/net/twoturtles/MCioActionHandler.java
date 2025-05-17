package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.twoturtles.mixin.client.MouseMixin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Processes incoming actions from the agent
 */
class MCioActionHandler {
    private final MinecraftClient client;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond recvPPS = new TrackPerSecond("ActionsReceived");

    // Track keys and buttons that are currently pressed so we can clear them on reset.
    private final InputManager keyManager;
    private final InputManager buttonManager;
    private volatile boolean pendingClearInput = false;

    MCioActionHandler(MinecraftClient client) {
        this.client = client;
        keyManager = new InputManager(InputManager.Type.KEY, client);
        buttonManager = new InputManager(InputManager.Type.BUTTON, client);
    }

    /**
     * Sync mode calls this from the Render (client) thread.
     * Async mode calls this from the ActionThread.
     * When on the Render thread, tasks passed to client.execute() will run immediately and synchronously.
     * Tasks passed from the ActionThread will run at a future time (not necessarily the next tick?) on the
     * Render thread
     */
    void processAction(ActionPacket action) {
        recvPPS.count();
        LOGGER.debug("ActionPacket: {}", action);

        /* Stop */
        if (action.stop()) {
            LOGGER.info("Received-Stop-Command");
            client.scheduleStop();
        }

        /* Clear input
           Intentionally done before processing new keys / buttons in this pkt. */
        if (action.clear_input()) {
            pendingClearInput = true;
        }
        if (pendingClearInput) {
            LOGGER.info("Running-Clear-Input");
            pendingClearInput = false;
            clearInput();
        }

        /* Commands */
        ClientPlayerEntity player = client.player;
        if (player != null) {
            for (String command : action.commands()) {
                LOGGER.info("Run-Command: {}", command);
                player.networkHandler.sendChatCommand(command);
            }
        }

        // Key / Mouse button handling
        for (Input input: action.inputs()) {
            switch (input.type()) {
                case KEY -> keyManager.update(input.code(), input.action());
                case MOUSE -> buttonManager.update(input.code(), input.action());
            }
        }

        for (double[] tuple : action.cursor_pos()) {
            client.execute(() -> {
                ((MouseMixinInterface) client.mouse).onCursorPosAgent$Mixin(
                        client.getWindow().getHandle(), tuple[0], tuple[1]);
            });
        }
    }

    /**
     * Clear all key / button presses and set cursor to 0,0
     */
    private void clearInput() {
        keyManager.clear();
        buttonManager.clear();
        client.execute(() -> {
            ((MouseMixin.MouseAccessor) client.mouse).setX(0.0);
            ((MouseMixin.MouseAccessor) client.mouse).setY(0.0);
        });
    }

    /**
     * Call from any thread to request that processAction clear inputs.
     * This is called from networking threads when connections go up/down.
     */
    public void requestClearInput() {
        pendingClearInput = true;
    }

}

// Send key/button events and track which are currently pressed.
class InputManager {
    private final Logger LOGGER = LogUtils.getLogger();
    public enum Type {
        KEY, BUTTON
    }
    public final Set<Integer> pressed = new HashSet<>();
    final InputManager.Type type;
    private final MinecraftClient client;

    InputManager(Type type, MinecraftClient client) {
        this.type = type;
        this.client = client;
    }

    // Update key / button state and track which are pressed
    // inputCode can be a keyCode or buttonCode, depending on Type.
    // Call from within client.execute().
    private void updateSingle(int inputCode, int actionCode) {
        long handle = client.getWindow().getHandle();

        if (type == Type.KEY) {
            client.keyboard.onKey(handle, inputCode, 0, actionCode, 0);
        } else if (type == Type.BUTTON) {
            ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(handle,
                    inputCode, actionCode, 0);
        }

        if (actionCode == GLFW.GLFW_PRESS) {
            this.pressed.add(inputCode);
        } else if (actionCode == GLFW.GLFW_RELEASE) {
            this.pressed.remove(inputCode);
        }
    }

    // Depending on mode, may be on action thread. Pass to client thread.
    public void update(int inputCode, int actionCode) {
        client.execute(() -> {
            updateSingle(inputCode, actionCode);
        });
    }

    // Clear all pressed inputs
    public void clear() {
        Set<Integer> pressedCopy = new HashSet<>(pressed);
        client.execute(() -> {
            for (int inputCode : pressedCopy) {
                updateSingle(inputCode, GLFW.GLFW_RELEASE);
            }
        });
    }

}
