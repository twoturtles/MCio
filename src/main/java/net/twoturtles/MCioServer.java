package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;

public class MCioServer implements ModInitializer {
	private final Logger LOGGER = LogUtils.getLogger();
	private final TrackPerSecond serverTPS = new TrackPerSecond("ServerTicks");
	MCioConfig config;
	private MCioServerSync serverSync;
	private MCioServerAsync serverAsync;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Main Init");
		config = MCioConfig.getInstance();

		ServerLifecycleEvents.SERVER_STARTED.register(client -> {
			LOGGER.info("Server Started");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(client -> {
			LOGGER.info("Server Stopping");
			stop();
		});

		/* Server Ticks */
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			serverTPS.count();
			LOGGER.debug("Server Tick End");
		});

		if (config.mode == MCioDef.Mode.SYNC) {
			serverSync = new MCioServerSync(config);
		} else if (config.mode == MCioDef.Mode.ASYNC){
			serverAsync = new MCioServerAsync(config);
		}

	}

	void stop() {
		if (config.mode == MCioDef.Mode.SYNC) {
			serverSync.stop();
		} else if (config.mode == MCioDef.Mode.ASYNC) {
			serverAsync.stop();
		}
	}
}

