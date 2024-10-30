package com.github.twoturtles;

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

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private static final Logger LOGGER = LogUtils.getLogger();
	private static KeyBinding captureKey;
	private static final String CATEGORY = "key.categories.framecapture";
	private static int frameCount = 0;

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");

		// Register the keybinding (default to F8)
		captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.framecapture.capture",
				GLFW.GLFW_KEY_F8,
				CATEGORY
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
				(message) -> {}		// No message to the UI
		);
	}
}
