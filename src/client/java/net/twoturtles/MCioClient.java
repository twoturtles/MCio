/**
 * Top-level file for code that runs on the Client (Render) thread
 */

package net.twoturtles;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private MinecraftClient clientMC;
	private MCioClientAsync clientAsync;
	private MCioClientSync clientSync;
	private final TrackPerSecond clientTPS = new TrackPerSecond("ClientTicks");
	MCioConfig config;
	boolean lanOpened = false;

	// Used by MinecraftClientMixin and MouseMixin
	public static boolean MCioWindowFocused;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client Init");
		clientMC = MinecraftClient.getInstance();
		config = MCioConfig.getInstance();
		MCioFrameSave.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			config.printSkins(MCioClientUtil.getDefaultSkins());
			LOGGER.info("Client-Started mode={}", config.mode);
			if (config.unlimitedFPS) {
				// Normal FPS limiting is disabled by RenderSystemMixin. This disables vsync.
				LOGGER.info("Disabling FPS limiting and VSYNC");
				clientMC.getWindow().setVsync(false);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Stopping");
			stop();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTPS.count();

			/* Open to LAN */
			boolean serverReady = client.getServer() != null && client.getNetworkHandler() != null;
			if (config.openToLan && serverReady && !lanOpened) {
				LOGGER.info("Open-To-LAN port={}", config.openLanToPort);
				// Even though it's a server method, Minecraft calls this from the Render thread.
				client.getServer().openToLan(config.openToLanMode, true, config.openLanToPort);
				lanOpened = true;
			}
		});

		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync = new MCioClientSync(config);
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync = new MCioClientAsync(config);
		}
		MCioFrameCapture.getInstance().setEnabled(true);

		MCioClientChunks.getInstance().clientSetup();
	}

	void stop() {
		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync.stop();
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync.stop();
		}
	}
}
