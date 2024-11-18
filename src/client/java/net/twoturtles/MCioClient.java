package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.option.KeyBinding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.twoturtles.util.TrackPerSecond;

class MCIO_CONST {
	public static final String KEY_CATEGORY = "MCio";
}

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private final MCioFrameSave fsave = new MCioFrameSave();
	private final MCioKeys keys = new MCioKeys();
	private MCioController mc_ctrl;

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");

		mc_ctrl = new MCioController();
		mc_ctrl.start();
		fsave.initialize();
		keys.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			LOGGER.info("Client Started");
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Stopping");
			if (mc_ctrl != null) {
				mc_ctrl.stop();
			}
		});
	}
}

// Handler for miscellaneous keyboard shortcuts
class MCioKeys {
	private final Logger LOGGER = LogUtils.getLogger();
	private KeyBinding breakKey, nextKey;
	private final TrackPerSecond clientTPS = new TrackPerSecond("ClientTicks");

	public void initialize() {
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
			if (breakKey.wasPressed() && client.world != null) {
				LOGGER.info("Break-Toggle");
				/* Tell server */
				MCioServer.isFrozen.set(!MCioServer.isFrozen.get());
			}
			if (nextKey.wasPressed() && client.world != null) {
				LOGGER.info("Next");
                assert client.player != null;
                client.player.networkHandler.sendCommand("tick step");
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();
		});
	}
}

