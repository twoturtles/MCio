package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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
	private final TrackPerSecond clientTPS = new TrackPerSecond("ClientTicks");

	public void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();
		});
	}
}

