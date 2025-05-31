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

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.twoturtles.mixin.ServerStatHandlerMixin;

public class MCioStats {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ServerPlayerEntity player;

    // Singleton instance
    private static final MCioStats INSTANCE = new MCioStats();
    public static MCioStats getInstance() {
        return INSTANCE;
    }

    private MCioStats() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (player == null) {
                // The first connection is the local player
                player = handler.getPlayer();
                writeFullStatsJson(player);
            }
        });
    }

    /**
     * Write the complete stats set to a file and exit
     */
    private void writeFullStatsJson(ServerPlayerEntity player) {
        initializeAllStatsForPlayer(player, 0);
        ServerStatHandler statHandler = player.getStatHandler();
        String result = ((ServerStatHandlerMixin.asStringInvoker) statHandler).invokeAsString();

        JsonElement element = JsonParser.parseString(result);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        result = gson.toJson(element);

        PrintStream stdout = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        stdout.println(result);
        System.exit(0);
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
