/**
 * Top-level file for code that runs on the Server (Main) thread
 * Note: "Server" here refers to the logical server that exists in both single-player and
 * dedicated server environments. In single-player, this runs within the client process.
 * Note 2: MCio currently only actively supports single-player environments.
 */
package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.world.GameRules;
import org.slf4j.Logger;

public class MCioServer implements ModInitializer {
	private final Logger LOGGER = LogUtils.getLogger();
	private final TrackPerSecond serverTPS = new TrackPerSecond("ServerTicks");
	private MCioConfig config;
	private MCioServerSync serverSync;
	private MCioServerAsync serverAsync;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		/* Note This is called by the Main thread, which eventually becomes the Render (client) thread.
		 * But most code in the main namespace is used by the Server thread or shared.
		 */
		LOGGER.info("Main-Init");
		config = MCioConfig.getInstance();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Server Started mode={}", config.mode);
			// Automatically disable chat messages about commands
			server.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK).set(false, server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server Stopping");
			stop();
		});

		/* Server Ticks */
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			serverTPS.count();
			LOGGER.debug("Server-Tick-End");
		});

		if (config.mode == MCioConfig.MCioMode.SYNC) {
			serverSync = new MCioServerSync(config);
		} else if (config.mode == MCioConfig.MCioMode.ASYNC){
			serverAsync = new MCioServerAsync(config);
		}

		MCioChunks.getInstance().serverSetup();
	}

	void stop() {
		if (config.mode == MCioConfig.MCioMode.SYNC) {
			serverSync.stop();
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			serverAsync.stop();
		}
	}
}

