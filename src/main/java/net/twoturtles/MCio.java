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
	private final MCioTickTimer server_timer = new MCioTickTimer("Server");
	private final MCioTickTimer world_timer = new MCioTickTimer("World");

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

class MCioTickTimer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final String name;
	private long startTime = 0, endTime = 0;
	private long tickCount = 0;

	public MCioTickTimer(String name) {
		this.name = name;
	}

	public void start() {
		startTime = System.nanoTime();
		if (tickCount % 20 == 0) {	/* 20 tps */
			String durr = String.format("%.2f", endTime > 0 ? (startTime - endTime) / 1_000_000.0 : 0);
			LOGGER.info("Tick Start: {} {} between ticks = {} ms", name, tickCount, durr);
		}
	}

	public void end() {
		endTime = System.nanoTime();
		if (tickCount % 20 == 0) {
			LOGGER.info("Tick End: {} tick time = {} ms", name,
					String.format("%.2f", (endTime - startTime) / 1_000_000.0));
		}
		tickCount++;
	}
}

