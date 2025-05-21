/**
 * Top-level file for code that runs on the Client (Render) thread
 */

package net.twoturtles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
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

	// XXX
	private int nChunks;
	private double start = System.nanoTime() / 1_000_000_000.0;

	// Used by MinecraftClientMixin and MouseMixin
	public static boolean MCioWindowFocused;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client Init");
		clientMC = MinecraftClient.getInstance();
		config = MCioConfig.getInstance();
		MCioFrameSave.initialize();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			LOGGER.info("Client Started mode={}", config.mode);
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
		});

		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync = new MCioClientSync(config);
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync = new MCioClientAsync(config);
		}
		MCioFrameCapture.getInstance().setEnabled(true);

		// XXX
		debug();
	}

	void stop() {
		if (config.mode == MCioConfig.MCioMode.SYNC) {
			clientSync.stop();
		} else if (config.mode == MCioConfig.MCioMode.ASYNC) {
			clientAsync.stop();
		}
	}

	// XXX
	void debug() {
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			nChunks++;
			double now = System.nanoTime() / 1_000_000_000.0;
			LOGGER.info("Client-Load-Chunk pos=[{}, {}] n={} time={}",
					chunk.getPos().x, chunk.getPos().z,
					nChunks, String.format("%.2f", now-start));
		});

		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			LOGGER.info("Client-Unload-Chunk pos=[{}, {}]",
					chunk.getPos().x, chunk.getPos().z);
		});
	}
}
