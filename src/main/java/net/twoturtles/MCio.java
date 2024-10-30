package net.twoturtles;

import com.mojang.logging.LogUtils;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;

public class MCio implements ModInitializer {
	private static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Main Init");
	}
}
