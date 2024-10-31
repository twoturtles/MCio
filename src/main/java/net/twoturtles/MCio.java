package net.twoturtles;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
//import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.ServerTickManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;

public class MCio implements ModInitializer {
	public static AtomicBoolean isFrozen = new AtomicBoolean(false);

	private static final Logger LOGGER = LogUtils.getLogger();
	private static int tickCount = 0;
	private static long tickStartTime = 0, tickEndTime = 0;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Main Init");

		// XXX Check out command TickSprint
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			tickStartTime = System.nanoTime();
			if (tickEndTime != 0) {
				long endToStart = tickStartTime - tickEndTime;
				LOGGER.info("Time between ticks {} ms",
						String.format("%.2f", (tickStartTime - tickEndTime) / 1_000_000.0));
			}
			if (tickCount % 20 == 0) {
				LOGGER.info("Server Tick Count {}", tickCount);
			}
			tickCount++;

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
			tickEndTime = System.nanoTime();
			long endToStart = tickStartTime - tickEndTime;
			LOGGER.info("Tick time {} ms",
					String.format("%.2f", (tickEndTime - tickStartTime) / 1_000_000.0));
		});
	}
}
