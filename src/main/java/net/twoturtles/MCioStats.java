package net.twoturtles;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;


import net.twoturtles.mixin.ServerStatHandlerMixin;

public class MCioStats {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Singleton instance
    private static final MCioStats INSTANCE = new MCioStats();
    public static MCioStats getInstance() {
        return INSTANCE;
    }

    // The handler associated with the local player
    private ServerStatHandler localHandler;
    private boolean doFullStats = false;

    /*
     * Shared between the client and server threads.
     */
    // Contains the full set of non-zero stats
    private Object2IntMap<Stat<?>> statMap = new Object2IntOpenHashMap<>();
    // Contains the stats that have been updated since the agent was last updated.
    private Set<Stat<?>> pendingStats = Sets.newHashSet();

    private MCioStats() {
//        // Register JOIN callback to initialize when the localPlayer is available
//        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
//            if (localPlayer == null) {
//                // The first connection is the localPlayer
//                localPlayer = handler.getPlayer();
//                if (doFullStats) {
//                    writeFullStatsSampleJson(localPlayer);
//                }
//            }
//        });
//
//        ServerTickEvents.END_SERVER_TICK.register(server -> {
//            if (localPlayer != null) {
//                // Update stats for the client to pick up
//                ServerStatHandler statHandler = localPlayer.getStatHandler();
////                updateCustomStats(statHandler);
//            }
//        });
    }

    // From ServerStatHandler.asString(). E.g., minecraft:mined
    public String getStatCategory(Stat<?> stat) {
        return Objects.toString(Registries.STAT_TYPE.getId(stat.getType()), "unknown");
    }

    // Based on ServerStatHandler.getId(). E.g., minecraft:grass_block
    public <T> String getStatId(Stat<T> stat) {
        return Objects.toString(stat.getType().getRegistry().getId(stat.getValue()), "unknown");
    }

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
        LOGGER.info("########### replaceStats {} {}", handler, localHandler);
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

    /* ******* */

    private void updateCustomStats(ServerStatHandler statHandler) {
        StatType<Identifier> customStatType = Stats.CUSTOM;

        for (Identifier id : customStatType.getRegistry()) {
            if (customStatType.hasStat(id)) {
                Stat<Identifier> stat = customStatType.getOrCreateStat(id);
                int value = statHandler.getStat(stat);
                System.out.printf("CUSTOM stat: %s (%s) = %d\n", id.toString(), stat.getName(), value);
            } else {
                System.out.printf("CUSTOM ZERO stat: %s\n", id.toString());
            }
        }
    }

    /**
     * Mark that the full stats dump should be done.
     * We have to wait until the localPlayer connects before the write can happen.
     */
    public void setDoFullStats() {
        doFullStats = true;
    }

    /**
     * Write the complete stats set to a file and exit
     * For development, to see what's available
     */
    private void writeFullStatsSampleJson(ServerPlayerEntity player) {
        initializeAllStatsForPlayer(player, 0);
        ServerStatHandler statHandler = player.getStatHandler();
        String resultJson = ((ServerStatHandlerMixin.asStringInvoker) statHandler).invokeAsString();

        // Redo json with pretty print
        JsonElement element = JsonParser.parseString(resultJson);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        resultJson = gson.toJson(element);

        Path path = player.server.getPath("full_stats.json");
        PrintStream stdout = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        stdout.printf("\n\n\nWriting Full Stats Set: %s\n\n\n", path.toString());
        try {
            Files.writeString(path, resultJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed-To-Write-Stats {}", path, e);
        }

        // Hard exit to prevent the localPlayer's stats file from
        // being overwritten with the zeros
        System.out.flush();
        System.err.flush();
        Runtime.getRuntime().halt(0);
    }

    /**
     * Populates every stat with defaultValue.
     * Usually only non-zero stats are saved. This forces all stats to save.
     */
    private void initializeAllStatsForPlayer(ServerPlayerEntity player, int defaultValue) {
        ServerStatHandler statHandler = player.getStatHandler();
        for (Field field : Stats.class.getDeclaredFields()) {
            // Skip all fields except static StatType.
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!StatType.class.isAssignableFrom(field.getType())) continue;

            try {
                @SuppressWarnings("unchecked")
                StatType<Object> statType = (StatType<Object>) field.get(null);
                for (Object value : statType.getRegistry()) {
                    Stat<Object> stat = statType.getOrCreateStat(value);
                    LOGGER.debug("setStat {} {}", player, stat);
                    statHandler.setStat(player, stat, defaultValue);
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Failed to initialize stats for type: {}", field.getName(), e);
            }
        }
    }


}
