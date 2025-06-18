package net.twoturtles;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import net.minecraft.registry.Registries;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import org.slf4j.Logger;

public class MCioStats {
  private static final Logger LOGGER = LogUtils.getLogger();

  // Singleton instance
  private static final MCioStats INSTANCE = new MCioStats();

  public static MCioStats getInstance() {
    return INSTANCE;
  }

  // The handler associated with the local player
  private ServerStatHandler localHandler;

  /*
   * Shared between the client and server threads.
   */
  // Contains the full set of non-zero stats
  private Object2IntMap<Stat<?>> statMap = new Object2IntOpenHashMap<>();
  // Contains the stats that have been updated since the agent was last updated.
  private Set<Stat<?>> pendingStats = Sets.newHashSet();

  private MCioStats() {}

  // The first init is the handler associated with the local player.
  public void onServerStatHandlerInit(ServerStatHandler handler) {
    if (localHandler == null) {
      localHandler = handler;
    }
  }

  /* ** Synchronized ** */

  // Called by server when a stat is updated
  public synchronized void updateStats(ServerStatHandler handler, Stat<?> stat, int value) {
    if (handler != localHandler) return;
    this.statMap.put(stat, value);
    this.pendingStats.add(stat);
  }

  // Called by server after the stats are loaded from json
  public synchronized void replaceStats(ServerStatHandler handler, Object2IntMap<Stat<?>> other) {
    if (handler != localHandler) return;
    this.statMap = new Object2IntOpenHashMap<>(other);
    this.pendingStats = Sets.newHashSet(this.statMap.keySet());
  }

  @FunctionalInterface
  public interface StatCallback {
    void call(String category, String id, int value);
  }

  // Called by the client to retrieve updated stats
  public synchronized void takePendingStats(boolean clear, StatCallback callback) {
    for (Stat<?> stat : pendingStats) {
      String category = getStatCategory(stat);
      String id = getStatId(stat);
      int value = statMap.getInt(stat);
      callback.call(category, id, value);
    }
    if (clear) {
      pendingStats.clear();
    }
  }

  // Called by the client to retrieve all stats
  public synchronized void statsForEach(StatCallback callback) {
    for (Object2IntMap.Entry<Stat<?>> entry : statMap.object2IntEntrySet()) {
      Stat<?> stat = entry.getKey();
      String category = getStatCategory(stat);
      String id = getStatId(stat);
      int value = entry.getIntValue();
      callback.call(category, id, value);
    }
  }

  /* *** Utilities *** */

  // From ServerStatHandler.asString(). E.g., minecraft:mined
  public static String getStatCategory(Stat<?> stat) {
    return Objects.toString(Registries.STAT_TYPE.getId(stat.getType()), "unknown");
  }

  // Based on ServerStatHandler.getId(). E.g., minecraft:grass_block
  public static <T> String getStatId(Stat<T> stat) {
    return Objects.toString(stat.getType().getRegistry().getId(stat.getValue()), "unknown");
  }

  /**
   * Returns a newline separated list of all stat names. E.g., minecraft.mined:minecraft.grass_block
   * minecraft.mined:minecraft.dirt minecraft.mined:minecraft.coarse_dirt ...
   */
  public static String getAllStatNames() {
    StringBuilder sb = new StringBuilder();

    for (Field field : Stats.class.getDeclaredFields()) {
      // Skip all fields except static StatType.
      if (!Modifier.isStatic(field.getModifiers())) continue;
      if (!StatType.class.isAssignableFrom(field.getType())) continue;

      try {
        @SuppressWarnings("unchecked")
        StatType<Object> statType = (StatType<Object>) field.get(null);
        for (Object value : statType.getRegistry()) {
          Stat<Object> stat = statType.getOrCreateStat(value);
          sb.append(String.format("%s %s\n", getStatCategory(stat), getStatId(stat)));
        }
      } catch (IllegalAccessException e) {
        LOGGER.error("Failed to get stats for type: {}", field.getName(), e);
      }
    }

    return sb.toString();
  }
}
