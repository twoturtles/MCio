package net.twoturtles;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.server.ServerTickManager;

import net.twoturtles.mixin.ServerTickManagerAccessor;

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
 *     1. START_CLIENT_TICK -> acquire client sem (Wait for previous server to finish tick) ->
 *       wait for action -> process action -> client tick ->
 *       continue render() -> frame capture callback -> generateObservation() ->
 *     2. END_CLIENT_TICK -> release server sem
 *     3. START_SERVER_TICK -> acquire server sem
 *     4. END_SERVER_TICK  -> release client sem
 *     ...
 *
 */
public class MCioSyncUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile boolean gameRunning = false;
    private final AtomicInteger cycleCount = new AtomicInteger();   // Track the loops through client/server ticks
    private MinecraftServer server;

    // Synchronize the transition to gameRunning
    private volatile boolean readyToSyncThreads = false;
    private final CyclicBarrier threadSyncBarrier = new CyclicBarrier(2, this::threadSyncDone);

    // Semaphores for tick control
    // Alternate client/server ticks once gameRunning is true
    private final Semaphore clientTickSem = new Semaphore(1);  // client goes first
    private final Semaphore serverTickSem = new Semaphore(0);

    // Create a context for each tick that defines its logic
    private final TickContext CLIENT_TICK = new TickContext("Client", clientTickSem, serverTickSem, true);
    private final TickContext SERVER_TICK = new TickContext("Server", serverTickSem, clientTickSem, false);

    // Singleton instance
    private static final MCioSyncUtil INSTANCE = new MCioSyncUtil();
    public static MCioSyncUtil getInstance() {
        return INSTANCE;
    }

    private MCioSyncUtil() { }

    public boolean isGameRunning() { return gameRunning; }

    public void clientStartTick() {
        startTick(CLIENT_TICK);
    }

    public void clientEndTick() {
        endTick(CLIENT_TICK);
    }

    public void serverStartTick() {
        startTick(SERVER_TICK);
    }

    public void serverEndTick() {
        endTick(SERVER_TICK);
    }

    /**
     * Sets the server to frozen. This prevents the world from ticking
     * before the agent is ready. Call at SERVER_STARTED.
     */
    public void serverInit(MinecraftServer svr) {
        server = svr;
        ServerTickManager tickManager = server.getTickManager();
        tickManager.setFrozen(true);
    }

    /**
     * For sync mode run Minecraft in sprint mode. This way there's no artificial delay between ticks.
     * It will go as fast as we step.
     */
    private void serverStartSprint() {
        ServerTickManager tickManager = server.getTickManager();
        tickManager.setFrozen(false);
        // Start the sprint with the normal API, then set the sprint to go forever.
        tickManager.startSprint(1);
        ((ServerTickManagerAccessor) tickManager).setSprintTicks(Long.MAX_VALUE);
        ((ServerTickManagerAccessor) tickManager).setScheduledSprintTicks(Long.MAX_VALUE);
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
            if (server.isOnThread()) {
                // About to transition to running. Set the server to sprint.
                serverStartSprint();
            }

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

    private void startTick(TickContext ctx) {
        handleThreadSyncTransition();
        if (gameRunning) {
            try {
                LOGGER.debug("{}-Waiting", ctx.label);
                ctx.acquireSem.acquire();
                if (ctx.incrementCycle) {
                    cycleCount.incrementAndGet();
                }
                LOGGER.debug("Cycle={} {}-Start-Tick", cycleCount.get(), ctx.label);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted", e);
            }
        }
    }

    private void endTick(TickContext ctx) {
        if (gameRunning) {
            LOGGER.debug("Cycle={} {}-End-Tick", cycleCount.get(), ctx.label);
            ctx.releaseSem.release();
        }
    }

    // TickContext to encapsulate tick-specific info
    private record TickContext(
            String label,
            Semaphore acquireSem,
            Semaphore releaseSem,
            boolean incrementCycle) { }
}