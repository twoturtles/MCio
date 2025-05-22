package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import org.slf4j.Logger;

public class ChunksDebugClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Singleton instance
    private static final ChunksDebugClient INSTANCE = new ChunksDebugClient();
    public static ChunksDebugClient getInstance() { return INSTANCE; }
    private ChunksDebugClient() { }

    void clientDebugSetup() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            ChunksDebug.getInstance().clientLoad(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ChunksDebug.getInstance().clientUnload(chunk);
        });
    }

}
