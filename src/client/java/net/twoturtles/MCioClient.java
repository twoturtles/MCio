package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/* TODO
 * - Teleport in actions to reset
 * - Ensure all calls to random come from the same seed? Seed from env
 * - Disable idle frame slowdown?
 *      client.getInactivityFpsLimiter()
 * - shared config file, override with env/command line option
 * - logging level config
 * - minerl compatible mode - find out other features to make it useful
 * - gymnasium
 * - tests - java and python
 * - Save and replay scripts
 * - Everything in client, so server could be run separately
 */

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

		if (config.mode == MCioDef.Mode.SYNC) {
			clientSync = new MCioClientSync(config);
			MCioFrameCapture.getInstance().setEnabled(true);
		} else if (config.mode == MCioDef.Mode.ASYNC) {
			clientAsync = new MCioClientAsync(config);
			MCioFrameCapture.getInstance().setEnabled(true);
		}

	}

	void stop() {
		if (config.mode == MCioDef.Mode.SYNC) {
			clientSync.stop();
		} else if (config.mode == MCioDef.Mode.ASYNC) {
			clientAsync.stop();
		}
	}
}

