package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class MCioUtil {
    static void sleep(double seconds) {
        MCioUtil.msleep((long) (seconds * 1000));
    }
    static void msleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    /* Return current time in seconds. */
    static double now() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}

/* Track and log some event per second */
class TrackPerSecond {
    private static final Logger LOGGER = LogUtils.getLogger();
    private double start = 0.0;
    private double startTotal = 0.0;
    private int count = 0;
    private int countTotal = 0;
    private double logTime = 10.0;
    private String name = "";

    TrackPerSecond(String name) { this.name = name; }
    TrackPerSecond(String name, double logTime) {
        this.name = name;
        this.logTime = logTime;
    }

    /* Count frames. Log every logTime seconds. Return true when logged. */
    boolean count() {
        if (start == 0.0) {
            start = MCioUtil.now();
            startTotal = start;
        }
        double end = MCioUtil.now();
        count++;
        countTotal++;
        if (end - start >= logTime) {
            double perSec = count / (end - start);
            LOGGER.info("{} per-second={}", name, String.format("%.1f", perSec));
            start = end;
            count = 0;
            return true;
        }
        return false;
    }

    public double getTotalPerSec() {
        return countTotal / (MCioUtil.now() - startTotal);
    }
    public int getTotal() {return countTotal;}
}

/* Keep only the most recent item. If a new item is added before the previous is removed,
 * the previous item is dropped. */
class LatestItemQueue<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private T item;
    private boolean logOnDrop = true;

    LatestItemQueue() {
    }
    LatestItemQueue(boolean logOnDrop) {
        this.logOnDrop = logOnDrop;
    }

    public synchronized void put(T item) {
        if (this.item != null && logOnDrop) {
            LOGGER.warn("Packet Drop {}", item.getClass().getSimpleName());
        }
        this.item = item;
        notifyAll();
    }

    public synchronized T get() {
        while (item == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                LOGGER.warn("Unexpected Interrupt");
            }
        }
        T result = item;
        item = null;
        return result;
    }

    // May return null
    public synchronized T getNoWait() {
        T result = item;
        item = null;
        return result;
    }
}
