package net.twoturtles.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/* Track and log fps */
public class TrackFPS {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double A_BILLION = 1_000_000_000.0;
    private double start = System.nanoTime() / A_BILLION;
    private int pkt_count = 0;
    private double log_time = 10.0;
    private String name = "";

    public TrackFPS(String name) { this.name = name; }
    public TrackFPS(String name, double log_time) {
        this.name = name;
        this.log_time = log_time;
    }

    /* Count frames. Log every log_time seconds. True return when logged. */
    public boolean count() {
        double end = System.nanoTime() / A_BILLION;
        pkt_count++;
        if (end - start >= log_time) {
            double pps = pkt_count / (end - start);
            LOGGER.warn("FPS {} {}", name, String.format("%.1f", pps));
            start = end;
            pkt_count = 0;
            return true;
        }
        return false;
    }
}

