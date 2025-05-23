package net.twoturtles;

import net.minecraft.client.MinecraftClient;

public class MCioClientSyncUtil {
    /**
     * Pass the game state from the client up to the main namespace.
     * This must be done here because MCioSyncUtil can't access MinecraftClient.
     */
    public static void checkAndSetGameRunning() {
        MinecraftClient client = MinecraftClient.getInstance();

        boolean chunksReady = !MCioConfig.getInstance().mcioPreloadChunks ||
                MCioChunks.getInstance().clientInitialLoadComplete();

        // A "Screen" is an overlay, like "Loading", so currentScreen is null when the game window is up.
        if (client.currentScreen == null && chunksReady) {
            MCioSyncUtil.getInstance().setGameRunning(true);
        }
    }
}
