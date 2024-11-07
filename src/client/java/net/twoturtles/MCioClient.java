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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.twoturtles.util.tickTimer;

class MCIO_CONST {
	public static final String KEY_CATEGORY = "MCio";
}

/* TODO
 * - Ensure all calls to random come from the same seed?
 */

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private final MCioFrameSave fcap = new MCioFrameSave();
	private final MCioKeys keys = new MCioKeys();
	private MCioController mc_ctrl;

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");
		tickTimer.do_log = false;

		mc_ctrl = new MCioController();
		mc_ctrl.start();
		fcap.initialize();
		keys.initialize();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Shutdown");
			if (mc_ctrl != null) {
				mc_ctrl.stop();
			}
		});

//		Thread stateThread = new Thread(this::testThread, "MCio-TestThread");
//		stateThread.start();
	}

	private void testThread() {
		LOGGER.info("Test Thread Starting");
		int count = 0;
		final MinecraftClient client = MinecraftClient.getInstance();

//		while (true) {
//			double x = ((Math.sin((count * 2 * Math.PI) / 100) + 1) / 2) * 1000;
//			double y = ((Math.cos((count * 2 * Math.PI) / 100) + 1) / 2) * 1000;
//			count++;
//
//			client.execute(() -> {
//				((MouseMixin.OnCursorPosInvoker) client.mouse).invokeOnCursorPos(
//						client.getWindow().getHandle(), x, y);
//			});
//
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				LOGGER.warn("Interrupted");
//			}
//		}
	}

}

class MCioFrameSave {
	private final Logger LOGGER = LogUtils.getLogger();
	private KeyBinding captureKey;
	private int frameCount = 0;

	public void initialize() {
		LOGGER.info("Init");

		// Register the keybinding (default to F8)
		captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioFrameSave",
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
	private final Logger LOGGER = LogUtils.getLogger();
	private KeyBinding breakKey, nextKey;
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

			if (breakKey.wasPressed() && client.world != null) {
				LOGGER.info("Break-Toggle");
				/* Tell server */
				MCio.isFrozen.set(!MCio.isFrozen.get());
			}
			if (nextKey.wasPressed() && client.world != null) {
				LOGGER.info("Next");
                assert client.player != null;
                client.player.networkHandler.sendCommand("tick step");
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(server -> {
			client_timer.end();
		});
	}
}

