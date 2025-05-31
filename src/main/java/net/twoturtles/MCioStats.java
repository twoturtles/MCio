package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
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

import net.twoturtles.mixin.ServerStatHandlerMixin;

public class MCioStats {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Singleton instance
    private static final MCioStats INSTANCE = new MCioStats();
    public static MCioStats getInstance() {
        return INSTANCE;
    }

    private ServerPlayerEntity player;
    private boolean doFullStats = false;
    // Signal the client thread to exit. Trigger in END_CLIENT_TICK.
    public volatile boolean stopRequested = false;

    private MCioStats() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (player == null) {
                // The first connection is the local player
                player = handler.getPlayer();
                if (doFullStats) {
                    writeFullStatsJson(player);
                }
            }
        });
    }

    /**
     * Mark that the full stats dump should be done.
     * We have to wait until the player connects before the write can happen.
     */
    public void setDoFullStats() {
        doFullStats = true;
    }

    /**
     * Write the complete stats set to a file and exit
     * For development, to see what's available
     */
    private void writeFullStatsJson(ServerPlayerEntity player) {
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

        stopRequested = true;
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
