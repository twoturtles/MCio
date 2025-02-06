package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * MCIO_MODE=sync refers to the agent and Minecraft being synchronized.
 * However, when in sync mode we also need to synchronize the Minecraft client and server threads.
 * That's what this class is for.
 * Until the game reaches the running state, both threads run freely. Once the game has started,
 * we synchronize the threads' ticks such that the client and server handoff from one to the other.
 * The client thread ticks first, then the server thread ticks, and back around.
 * This isn't a perfect solution since the client won't have the server's updates from
 * the current action when the observation is generated. But at least it will be consistent.
 * Will revisit if necessary. I think the only way to be fully up-to-date when the
 * observation is generated is to run two client ticks and one server tick for every step -
 * Action arrives - client tick - server tick (update the client) - client tick again to integrate server updates,
 * and generate the observation. It would be nice to avoid this.
 *
 * Order of events (once the game is running)
 * MinecraftClient.run() -> game loop ->
 *     MinecraftClient.render() [Mojang calls this runTick()] -> MinecraftClient.tick() ->
 *     START_CLIENT_TICK -> Wait for previous server to finish tick -> wait for action -> process action -> client tick ->
 *     continue render() -> frame capture callback -> generateObservation() ->
 *     END_CLIENT_TICK -> START_SERVER_TICK (waiting for end client tick) ->
 *     END_SERVER_TICK (signal client to start) -> ...
 *
 */
public class MCioSyncUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile boolean gameRunning = false;

    // Synchronize the transition to gameRunning
    private volatile boolean readyToSyncThreads = false;
    private final CyclicBarrier threadSyncBarrier = new CyclicBarrier(2,this::threadSyncDone);

    // Alternate ticks once gameRunning is true
    private final Semaphore clientTickSem = new Semaphore(1);   // client thread goes first
    private final Semaphore serverTickSem = new Semaphore(0);

    // Singleton instance
    private static final MCioSyncUtil INSTANCE = new MCioSyncUtil();
    public static MCioSyncUtil getInstance() {
        return INSTANCE;
    }

    private MCioSyncUtil() { }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public void serverStartTick() {
        startTick(serverTickSem, "Server");   // Acquire server
    }
    public void serverEndTick() {
        endTick(clientTickSem, "Client");     // Release client
    }

    public void clientStartTick() {
        startTick(clientTickSem, "Client");   // Acquire client
    }
    public void clientEndTick() {
        endTick(serverTickSem, "Server");     // Release server
    }

    // This should be called via MCioClientSyncUtil.checkAndSetGameRunning().
    public void setGameRunning(boolean gameRunning) {
        // I think we only need to handle the transition to running
        if (!this.gameRunning && gameRunning) {
            // Trigger the transition
            LOGGER.info("gameRunning=true");
            readyToSyncThreads = true;
        }
    }

    private void handleThreadSyncTransition() {
        if (!gameRunning && readyToSyncThreads) {
            try {
                // Both threads block here and then threadSyncDone() is called
                LOGGER.info("Synchronizing Threads: {}", Thread.currentThread().getName());
                threadSyncBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void threadSyncDone() {
        LOGGER.info("Client-Server Sync Complete");
        gameRunning = true;
        readyToSyncThreads = false;
    }

    private void startTick(Semaphore sem, String label) {
        handleThreadSyncTransition();
        if (gameRunning) {
            try {
                LOGGER.debug("Wait semaphore={} thread={}", label, Thread.currentThread().getName());
                sem.acquire();
                LOGGER.debug("Acquired semaphore={} thread={}", label, Thread.currentThread().getName());
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted", e);
            }
        }
    }
    private void endTick(Semaphore sem, String label) {
        if (gameRunning) {
            LOGGER.debug("Release semaphore={} thread={}", label, Thread.currentThread().getName());
            sem.release();
        }
    }

}
