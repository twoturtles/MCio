package net.twoturtles.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class TickTimer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String name;
    private long startTime = 0, endTime = 0;

    public long tickCount = 0;
    public static boolean do_log = true;
    public int ticksBetweenLogs = 60;   // Minecraft runs at 20 ticks per second

    public TickTimer(String name) {
        this.name = name;
    }

    public void start() {
        startTime = System.nanoTime();
        if (tickCount % ticksBetweenLogs == 0) {
            if (do_log) {
                String durr = String.format("%.2f", endTime > 0 ? (startTime - endTime) / 1_000_000.0 : 0);
                LOGGER.info("{}-Tick-Start tickCount={} time-between-ticks={} ms", name, tickCount, durr);
            }
        }
    }

    public void end() {
        endTime = System.nanoTime();
        if (tickCount % ticksBetweenLogs == 0) {
            if (do_log) {
                LOGGER.info("{}-Tick-End tickCount={} tick-duration={} ms", name, tickCount,
                        String.format("%.2f", (endTime - startTime) / 1_000_000.0));
            }
        }
        tickCount++;
    }

}
