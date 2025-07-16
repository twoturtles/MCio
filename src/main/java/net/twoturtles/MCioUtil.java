package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/** Misc small utilities */
public class MCioUtil {
  /* Minecraft somehow captures System.out, so make a new stdout available. */
  public static PrintStream stdout =
      new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));

  /* Return current time in seconds. */
  public static double now() {
    return System.nanoTime() / 1_000_000_000.0;
  }

  /* Sleep utils */
  public static void sleep(double seconds) {
    MCioUtil.msleep((long) (seconds * 1000));
  }

  public static void msleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /* I can never remember the right call for this */
  public static void hardExit(int status) {
    Runtime.getRuntime().halt(status);
  }

  /**
   * To help with exploring code. Tracks unique stack traces to understand how a piece of code is
   * called.
   */
  public static class StackTraceCounter {
    private final Map<String, Integer> traceCounts = new ConcurrentHashMap<>();
    PrintStream out = MCioUtil.stdout;

    /**
     * Records the current stack trace by slicing the trace from [start] to [end] (inclusive). This
     * range helps skip internal frames and capture meaningful callers.
     */
    public void record(int start, int end) {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      String key = formatTrace(stack, start, end);
      traceCounts.merge(key, 1, Integer::sum);
    }

    /** Prints all collected trace counts to stdout. */
    public void printStats() {
      out.printf("=== Stack Trace Counts === %d unique traces\n", traceCounts.size());
      traceCounts.entrySet().stream()
          .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
          .forEach(
              entry -> {
                out.println("Count: " + entry.getValue());
                out.println(entry.getKey());
              });
    }

    private String formatTrace(StackTraceElement[] trace, int start, int end) {
      StringBuilder sb = new StringBuilder();
      if (end < 0) end = Integer.MAX_VALUE;
      for (int i = start; i <= end && i < trace.length; i++) {
        sb.append(String.format("%2d: %s%n", i, trace[i]));
      }
      return sb.toString();
    }
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

  TrackPerSecond(String name) {
    this.name = name;
  }

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

  public int getTotal() {
    return countTotal;
  }

  public void logTotal() {
    LOGGER.info(
        "{} total={} total-per-second={}",
        name,
        getTotal(),
        String.format("%.1f", getTotalPerSec()));
  }
}

/* Keep only the most recent item. If a new item is added before the previous is removed,
 * the previous item is dropped. */
class LatestItemQueue<T> {
  private static final Logger LOGGER = LogUtils.getLogger();
  private T item;
  private boolean logOnDrop = true;

  LatestItemQueue() {}

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
