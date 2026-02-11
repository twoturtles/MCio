package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import org.slf4j.Logger;

/**
 * Client-side bridge for chunk events. Forwards ClientChunkEvents to MCioChunks (which lives in the
 * main source set and can't access client-only APIs directly).
 */
public class MCioClientChunks {
  private static final Logger LOGGER = LogUtils.getLogger();

  // Singleton instance
  private static final MCioClientChunks INSTANCE = new MCioClientChunks();

  public static MCioClientChunks getInstance() {
    return INSTANCE;
  }

  private MCioClientChunks() {}

  void clientSetup() {
    ClientChunkEvents.CHUNK_LOAD.register(
        (world, chunk) -> {
          MCioChunks.getInstance().clientLoad(chunk);
        });

    ClientChunkEvents.CHUNK_UNLOAD.register(
        (world, chunk) -> {
          MCioChunks.getInstance().clientUnload(chunk);
        });
  }
}
