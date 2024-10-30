package net.twoturtles;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.mojang.logging.LogUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

class MCIO_CONST {
	public static final String KEY_CATEGORY = "MCio";
}

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final MCioFrameCapture fcap = new MCioFrameCapture();
	private static final MCioIO mcio = new MCioIO();

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");

		fcap.initialize();
		mcio.initialize();
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
				captureFrame(client);
			}
		});
	}

	private void captureFrame(MinecraftClient client) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		String fileName = String.format("frame_%s_%d.png", timestamp, frameCount++);
		LOGGER.info("Captured frame: " + fileName);

		// Capture the frame using Minecraft's screenshot recorder
		ScreenshotRecorder.saveScreenshot(
				client.runDirectory,
				fileName,
				client.getFramebuffer(),
				(message) -> {}       // No message to the UI
		);
	}
}

class MCioIO {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static KeyBinding breakKey, nextKey, contKey;
	private static int tickCount = 0;

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
		contKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioIO_continue",
				GLFW.GLFW_KEY_C,
				MCIO_CONST.KEY_CATEGORY
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tickCount++;
			if (breakKey.wasPressed() && client.world != null) {
				LOGGER.info("BREAK {}", tickCount);
				client.player.networkHandler.sendCommand("tick freeze");
			}
			if (nextKey.wasPressed() && client.world != null) {
				LOGGER.info("NEXT {}", tickCount);
				client.player.networkHandler.sendCommand("tick step");
			}
			if (contKey.wasPressed() && client.world != null) {
				LOGGER.info("CONT {}", tickCount);
				client.player.networkHandler.sendCommand("tick unfreeze");
			}
		});
	}
}
