package net.twoturtles;

import java.text.SimpleDateFormat;
import java.util.Date;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.mojang.logging.LogUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.twoturtles.util.tickTimer;
import net.twoturtles.mixin.client.MouseOnCursorPosInvoker;

class MCIO_CONST {
	public static final String KEY_CATEGORY = "MCio";
}

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final MCioFrameCapture fcap = new MCioFrameCapture();
	private static final MCioKeys keys = new MCioKeys();

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");

		fcap.initialize();
		keys.initialize();
	}
}

class MCioFrameCapture {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static KeyBinding captureKey;
	private static int frameCount = 0;

	public void initialize() {
		LOGGER.info("Init");

		// Register the keybinding (default to F8)
		captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioFrameCapture",
				GLFW.GLFW_KEY_F8,
				MCIO_CONST.KEY_CATEGORY
		));

		// Register the tick event
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (captureKey.wasPressed() && client.world != null) {
				doCapture(client);
			}
		});
	}

	private void doCapture(MinecraftClient client) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		String fileName = String.format("frame_%s_%d.png", timestamp, frameCount++);
		LOGGER.info("Captured frame: {}", fileName);

		// Capture the frame using Minecraft's screenshot recorder
		ScreenshotRecorder.saveScreenshot(
				client.runDirectory,
				fileName,
				client.getFramebuffer(),
				(message) -> {}       // No message to the UI
		);
	}
}

class MCioKeys {
	private static final Logger LOGGER = LogUtils.getLogger();
	/* XXX static? */
	private static KeyBinding breakKey, nextKey;
	private final tickTimer client_timer = new tickTimer("Client");

	public void initialize() {
		LOGGER.info("Init");

		breakKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioIO_break",
				GLFW.GLFW_KEY_B,
				MCIO_CONST.KEY_CATEGORY
		));
		nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioIO_next",
				GLFW.GLFW_KEY_N,
				MCIO_CONST.KEY_CATEGORY
		));
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			client_timer.start();

//			if (client_timer.tickCount == 100) {
//				LOGGER.warn("PRESS");
//				client.execute(() -> {
//					client.keyboard.onKey(client.getWindow().getHandle(),
//							GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_PRESS, 0);
//				});
//			} else if (client_timer.tickCount == 200) {
//				LOGGER.warn("RELEASE");
//				client.execute(() -> {
//					client.keyboard.onKey(client.getWindow().getHandle(),
//							GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_RELEASE, 0);
//				});
//			}
			if (client_timer.tickCount == 100) {
				LOGGER.warn("ONE");

				client.execute(() -> {
					((MouseOnCursorPosInvoker) client.mouse).invokeOnCursorPos(
							client.getWindow().getHandle(), 300.0, 300.0);
				});

			} else if (client_timer.tickCount == 200) {
				LOGGER.warn("TWO");
				client.execute(() -> {
					((MouseOnCursorPosInvoker) client.mouse).invokeOnCursorPos(
							client.getWindow().getHandle(), 250, 250.0);
				});

			} else if (client_timer.tickCount == 300) {
				LOGGER.warn("THREE");
				client.execute(() -> {
					((MouseOnCursorPosInvoker) client.mouse).invokeOnCursorPos(
							client.getWindow().getHandle(), 350.0, 350.0);
				});
		}

			if (breakKey.wasPressed() && client.world != null) {
				LOGGER.info("Break-Toggle");
				/* Tell server */
				MCio.isFrozen.set(!MCio.isFrozen.get());
			}
			if (nextKey.wasPressed() && client.world != null) {
				LOGGER.info("Next");
				client.player.networkHandler.sendCommand("tick step");
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(server -> {
			client_timer.end();
		});
	}
}

