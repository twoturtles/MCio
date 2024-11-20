package net.twoturtles;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import net.minecraft.server.ServerTickManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;

import net.twoturtles.util.TrackPerSecond;

public class MCioServer implements ModInitializer {
	public static AtomicBoolean isFrozen = new AtomicBoolean(false);

	private final Logger LOGGER = LogUtils.getLogger();
	private final TrackPerSecond serverTPS = new TrackPerSecond("ServerTicks");
	private final TrackPerSecond worldTPS = new TrackPerSecond("WorldTicks");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Main Init");

		// XXX Check out command TickSprint
		/* Server Ticks */
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			LOGGER.debug("Server Tick Start");
			ServerTickManager tickManager = server.getTickManager();
			if (isFrozen.get()) {	/* Want frozen */
				if (!tickManager.isFrozen()) {
					LOGGER.info("Freeze");
					tickManager.setFrozen(true);
				}
			} else {	/* Want unfrozen */
				if (tickManager.isFrozen()) {
					LOGGER.info("Unfreeze");
					tickManager.setFrozen(false);
				}
			}

		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			serverTPS.count();
			LOGGER.debug("Server Tick End");
		});


		/* World Ticks */
		ServerTickEvents.START_WORLD_TICK.register(serverWorld -> {
			LOGGER.debug("World Tick Start {}", serverWorld.getRegistryKey().getValue().toString());
		});
		ServerTickEvents.END_WORLD_TICK.register(serverWorld -> {
			worldTPS.count();
			LOGGER.debug("World Tick End {}", serverWorld.getRegistryKey().getValue().toString());
		});
	}
}

