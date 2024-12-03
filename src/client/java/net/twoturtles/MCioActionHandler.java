package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.twoturtles.mixin.client.MouseMixin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

// Processes incoming actions from the agent
class MCioActionHandler {
    private final MinecraftClient client;

    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond recvPPS = new TrackPerSecond("ActionsReceived");

    MCioActionHandler(MinecraftClient client) {
        this.client = client;
    }

    void processAction(ActionPacket action) {
        recvPPS.count();

        /* Commands */
        ClientPlayerEntity player = client.player;
        if (player != null) {
            for (String command : action.commands()) {
                LOGGER.info("Run Command: {}", command);
                player.networkHandler.sendChatCommand(command);
            }
        }

        /* Keyboard handler */
        for (int[] tuple : action.keys()) {
            int keyCode = tuple[0];
            int actionCode = tuple[1];
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        keyCode, 0, actionCode, 0);
            });
        }

        /* Mouse handler */
        for (int[] tuple : action.mouse_buttons()) {
            int buttonCode = tuple[0];
            int actionCode = tuple[1];
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), buttonCode, actionCode, 0);
            });
        }
        for (int[] tuple : action.cursor_pos()) {
            client.execute(() -> {
                ((MouseMixinInterface) client.mouse).onCursorPosAgent$Mixin(
                        client.getWindow().getHandle(), tuple[0], tuple[1]);
            });
        }
    }
}
