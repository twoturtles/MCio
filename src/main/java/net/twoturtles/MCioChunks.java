package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * Tracks initial chunk loading through three phases to determine when it's safe to start ticking.
 * Fast game ticks starve chunk loading, so in sync mode we delay ticks until chunks are ready.
 *
 * <p>Phase 1 - Select: The mixin on ChunkTrackingView.Positioned.forEach counts how many chunks the
 * server selects to send to the player. This sets the target count.
 *
 * <p>Phase 2 - Server: ServerChunkEvents count server-side loads until they match the select count.
 *
 * <p>Phase 3 - Client: ClientChunkEvents (via MCioClientChunks) count client-side loads until they
 * match the select count. When complete, clientInitialLoadComplete() returns true.
 *
 * <p>MCioClientSyncUtil checks clientInitialLoadComplete() to gate setGameRunning(), which controls
 * when sync mode begins ticking.
 */
public class MCioChunks {
  private static final Logger LOGGER = LogUtils.getLogger();

  // State flags for each phase
  private boolean selectStarted = false;
  private boolean selectEnded = false;
  private boolean serverStarted = false;
  private boolean serverEnded = false;
  private boolean clientStarted = false;
  private volatile boolean clientEnded =
      false; // volatile so render thread can read without locking
  // Per-phase counters. Each phase is complete when its count matches selectPerSec's total.
  private final TrackPerSecond selectPerSec = new TrackPerSecond("SelectChunks");
  private final TrackPerSecond serverPerSec = new TrackPerSecond("ServerChunks");
  private final TrackPerSecond clientPerSec = new TrackPerSecond("ClientChunks");

  // Singleton instance
  private static final MCioChunks INSTANCE = new MCioChunks();

  public static MCioChunks getInstance() {
    return INSTANCE;
  }

  private MCioChunks() {}

  // Select
  public synchronized void selectStart() {
    selectStarted = true;
  }

  public synchronized void selectTrack(int x, int z) {
    LOGGER.debug("Select-Chunk {} {}", x, z);
    if (selectStarted && !selectEnded) {
      selectPerSec.count();
    }
  }

  public synchronized void selectEnd() {
    selectEnded = true;
    selectPerSec.logTotal();
  }

  // Server
  void serverSetup() {
    ServerChunkEvents.CHUNK_LOAD.register(
        (world, chunk) -> {
          serverLoad(chunk);
        });
    ServerChunkEvents.CHUNK_UNLOAD.register(
        (world, chunk) -> {
          serverUnload(chunk);
        });
  }

  public synchronized void serverLoad(LevelChunk chunk) {
    // Note: The first loads are for spawn chunks
    if (selectStarted && !serverStarted) {
      LOGGER.info("ServerChunks Started");
      serverStarted = true;
    }
    if (selectStarted && !serverEnded) {
      serverPerSec.count();
    }
    ChunkPos pos = chunk.getPos();
    LOGGER.debug("Server-Load-Chunk [{}, {}]", pos.x, pos.z);

    if (selectEnded && !serverEnded && serverPerSec.getTotal() == selectPerSec.getTotal()) {
      serverEnded = true;
      serverPerSec.logTotal();
    }
  }

  public synchronized void serverUnload(LevelChunk chunk) {
    ChunkPos pos = chunk.getPos();
    LOGGER.debug("Server-Unload-Chunk [{}, {}]", pos.x, pos.z);
  }

  // Client
  public synchronized void clientLoad(LevelChunk chunk) {
    if (selectStarted && !clientStarted) {
      LOGGER.info("ClientChunks Started");
      clientStarted = true;
    }
    if (selectStarted && !clientEnded) {
      clientPerSec.count();
    }
    ChunkPos pos = chunk.getPos();
    LOGGER.debug("Client-Load-Chunk [{}, {}]", pos.x, pos.z);

    if (selectEnded && !clientEnded && clientPerSec.getTotal() == selectPerSec.getTotal()) {
      clientEnded = true;
      clientPerSec.logTotal();
    }
  }

  public synchronized void clientUnload(LevelChunk chunk) {
    ChunkPos pos = chunk.getPos();
    LOGGER.debug("Client-Unload-Chunk [{}, {}]", pos.x, pos.z);
  }

  public boolean clientInitialLoadComplete() {
    return clientEnded;
  }
}
