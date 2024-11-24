package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/* TODO
 * - Ensure all calls to random come from the same seed?
 * - step mode to allow stepping by ticks. Also allow above realtime speed.
 * - Disable idle frame slowdown?
 *      client.getInactivityFpsLimiter()
 * - shared config file, override with env/command line option
 * - separate logging with level config
 * - Command line args / config to start in paused state
 * - minerl compatible mode - find out other features to make it useful
 * - gymnasium
 * - tests - java and python
 * - Save and replay scripts
 * - Asynchronous and synchronous modes
 * - Everything in client, so server could be run separately
 * - Bind both sockets in minecraft? Would this fix zmq slow joiner?
 */

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private final MCioFrameSave fsave = new MCioFrameSave();
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

		if (config.mode == MCioMode.SYNC) {
			clientSync = new MCioClientSync(config);
		} else {
			clientAsync = new MCioClientAsync(config);
		}

		fsave.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			LOGGER.info("Client Started");
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Stopping");
			if (clientSync != null) {
				clientSync.stop();
			} else {
				clientAsync.stop();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();
		});

	}
}

