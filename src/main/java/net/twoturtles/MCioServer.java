package net.twoturtles;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.ServerTickManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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
		LOGGER.info("Main Init");
		config = new MCioConfig();

		if (config.mode == MCioMode.SYNC) {
			serverSync = new MCioServerSync(config);
		} else {
			serverAsync = new MCioServerAsync(config);
		}

		/* Server Ticks */
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			serverTPS.count();
			LOGGER.debug("Server Tick End");
		});

		/* World Ticks */
		ServerTickEvents.START_WORLD_TICK.register(serverWorld -> {
			LOGGER.debug("World Tick Start {}", serverWorld.getRegistryKey().getValue().toString());
		});
		ServerTickEvents.END_WORLD_TICK.register(serverWorld -> {
			LOGGER.debug("World Tick End {}", serverWorld.getRegistryKey().getValue().toString());
		});
	}
}

