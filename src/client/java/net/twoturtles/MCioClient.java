/**
 * Top-level file for code that runs on the Client (Render) thread
 */

package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private MCioClientAsync clientAsync;
	private MCioClientSync clientSync;
	private final TrackPerSecond clientTPS = new TrackPerSecond("ClientTicks");
	MCioConfig config;

	// Used by MinecraftClientMixin and MouseMixin
	public static boolean MCioWindowFocused;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client Init");
		config = MCioConfig.getInstance();
		MCioFrameSave.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			LOGGER.info("Client Started mode={}", config.mode);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Stopping");
			stop();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();
		});

		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync = new MCioClientSync(config);
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync = new MCioClientAsync(config);
		}
		MCioFrameCapture.getInstance().setEnabled(true);
	}

	void stop() {
		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync.stop();
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync.stop();
		}
	}
}
