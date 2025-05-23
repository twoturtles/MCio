package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import org.slf4j.Logger;

public class MCioClientChunks {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Singleton instance
    private static final MCioClientChunks INSTANCE = new MCioClientChunks();
    public static MCioClientChunks getInstance() { return INSTANCE; }
    private MCioClientChunks() { }

    void clientDebugSetup() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MCioChunks.getInstance().clientLoad(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            MCioChunks.getInstance().clientUnload(chunk);
        });
    }

}
