package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class MCioUtil {
    static void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

/* Track and log some event per second */
class TrackPerSecond {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double A_BILLION = 1_000_000_000.0;
    private double start = System.nanoTime() / A_BILLION;
    private int count = 0;
    private double logTime = 10.0;
    private String name = "";

    TrackPerSecond(String name) { this.name = name; }
    TrackPerSecond(String name, double logTime) {
        this.name = name;
        this.logTime = logTime;
    }

    /* Count frames. Log every logTime seconds. Return true when logged. */
    boolean count() {
        double end = System.nanoTime() / A_BILLION;
        count++;
        if (end - start >= logTime) {
            double pps = count / (end - start);
            LOGGER.info("{} per-second={}", name, String.format("%.1f", pps));
            start = end;
            count = 0;
            return true;
        }
        return false;
    }
}
