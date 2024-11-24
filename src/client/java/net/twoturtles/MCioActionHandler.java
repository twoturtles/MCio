package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.twoturtles.mixin.client.MouseMixin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.zeromq.ZContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Processes incoming actions from the agent
public class MCioActionHandler {
    private final MinecraftClient client;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond recvPPS = new TrackPerSecond("ActionsReceived");

    // Track keys and buttons that are currently pressed so we can clear them on reset.
    private final Set<Integer> keysPressed = new HashSet<>();
    private final Set<Integer> buttonsPressed = new HashSet<>();

    // Set at the end of processing an ActionPacket. Picked up by the State thread.
    // XXX public int lastSequenceProcessed = 0;

    /* XXX Clear all actions if remote controller disconnects? */
    public MCioActionHandler(MinecraftClient client) {
        this.client = client;
    }

    private void processAction(ActionPacket action) {
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

            // XXX this.controller.stateHandler.doSequenceReset.set(true);
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

        // XXX lastSequenceProcessed = action.sequence();
    }
}
