package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class MCioConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum MCioMode {
        OFF, SYNC, ASYNC;
    }

    // Constants
    public static final int MCIO_PROTOCOL_VERSION = 2;
    public static final String KEY_CATEGORY = "MCio";
    public static final String DEFAULT_HOST = "localhost";

    // Configurable
    public MCioMode mode;
    public int actionPort;
    public int observationPort;

    // Defaults
    private static final MCioMode DEFAULT_MCIO_MODE = MCioMode.ASYNC;
    private static final int DEFAULT_ACTION_PORT = 4001; // For receiving 4ctions
    private static final int DEFAULT_OBSERVATION_PORT = 8001;    // For sending 8bservations

    // Singleton instance
    private static final MCioConfig INSTANCE = new MCioConfig();
    public static MCioConfig getInstance() {
        return INSTANCE;
    }

    private MCioConfig() {
        mode = getEnvMode("MCIO_MODE", DEFAULT_MCIO_MODE);
        actionPort = getEnvInt("MCIO_ACTION_PORT", DEFAULT_ACTION_PORT);
        observationPort = getEnvInt("MCIO_OBSERVATION_PORT", DEFAULT_OBSERVATION_PORT);

        LOGGER.info("MCIO_MODE={}", mode);
        LOGGER.info("MCIO_ACTION_PORT={}", actionPort);
        LOGGER.info("MCIO_OBSERVATION_PORT={}", observationPort);
    }

    // Helper methods for parsing environment variables
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static MCioMode getEnvMode(String key, MCioMode defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return MCioMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}