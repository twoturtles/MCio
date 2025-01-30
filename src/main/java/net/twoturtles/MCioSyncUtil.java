package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.concurrent.Semaphore;

public class MCioSyncUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean gameRunning = false;
    private final Semaphore tickSem = new Semaphore(0);

    // Singleton instance
    private static final MCioSyncUtil INSTANCE = new MCioSyncUtil();
    public static MCioSyncUtil getInstance() {
        return INSTANCE;
    }

    private MCioSyncUtil() { }

    public boolean isGameRunning() {
        return gameRunning;
    }

    // This should be called via MCioClientSyncUtil.checkAndSetGameRunning().
    public void setGameRunning(boolean gameRunning) {
        if (!this.gameRunning && gameRunning) {
            LOGGER.info("GameRunning=true");
        }
        this.gameRunning = gameRunning;
    }

    public void waitForClientTick() {
        try {
            tickSem.acquire();
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted", e);
        }
    }

    public void tellServerToTick() {
        tickSem.drainPermits();
        tickSem.release();
    }

}
