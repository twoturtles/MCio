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
	private MCioClientAsync clientAsync;
	private final TrackPerSecond clientTPS = new TrackPerSecond("ClientTicks");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client Init");

		clientAsync = new MCioClientAsync();
		clientAsync.start();
		fsave.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			LOGGER.info("Client Started");
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Stopping");
			if (clientAsync != null) {
				clientAsync.stop();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();
		});

	}
}

