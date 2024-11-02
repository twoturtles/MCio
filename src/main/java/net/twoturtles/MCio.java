package net.twoturtles;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
//import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.ServerTickManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;

import net.twoturtles.util.tickTimer;

public class MCio implements ModInitializer {
	public static AtomicBoolean isFrozen = new AtomicBoolean(false);

	private static final Logger LOGGER = LogUtils.getLogger();
	private final tickTimer server_timer = new tickTimer("Server");
	private final tickTimer world_timer = new tickTimer("World");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Main Init");

		// XXX Check out command TickSprint
		/* Server Ticks */
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			server_timer.start();

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
			server_timer.end();
		});


		/* World Ticks */
		ServerTickEvents.START_WORLD_TICK.register(server -> {
			world_timer.start();
		});
		ServerTickEvents.END_WORLD_TICK.register(server -> {
			world_timer.end();
		});
	}
}

