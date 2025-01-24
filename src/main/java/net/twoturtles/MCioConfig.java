package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class MCioConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum MCioMode {
        OFF, SYNC, ASYNC;
    }
    public enum MCioFrameType {
        RAW, PNG, JPEG;
    }

    // Constants
    public static final int MCIO_PROTOCOL_VERSION = 3;
    public static final String KEY_CATEGORY = "MCio";
    public static final String DEFAULT_HOST = "localhost";

    // Configurable
    public MCioMode mode;
    public MCioFrameType frameType;
    public int frameQuality;
    public boolean unlimitedFPS;   // Disable Minecraft FPS limiting
    public int actionPort;
    public int observationPort;
    public boolean hideMinecraftWindow;
    public boolean doRetinaHack;

    // Defaults
    public static final MCioMode DEFAULT_MCIO_MODE = MCioMode.ASYNC;
    public static final MCioFrameType DEFAULT_MCIO_FRAME_TYPE = MCioFrameType.RAW;
    public static final int DEFAULT_FRAME_QUALITY = 85; // Only used for JPEG frames
    public static final boolean DEFAULT_UNLIMITED_FPS_SYNC = true;
    public static final boolean DEFAULT_UNLIMITED_FPS_ASYNC = false;
    public static final int DEFAULT_ACTION_PORT = 4001; // For receiving 4ctions
    public static final int DEFAULT_OBSERVATION_PORT = 8001;    // For sending 8bservations
    public static final boolean DEFAULT_HIDE_MINECRAFT_WINDOW = false;
    public static final boolean DEFAULT_DO_RETINA_HACK = true;  // Disable retina double resolution

    // Singleton instance
    private static final MCioConfig INSTANCE = new MCioConfig();
    public static MCioConfig getInstance() {
        return INSTANCE;
    }

    private MCioConfig() {
        mode = getEnvEnum("MCIO_MODE", DEFAULT_MCIO_MODE);
        frameType = getEnvEnum("MCIO_FRAME_TYPE", DEFAULT_MCIO_FRAME_TYPE);
        frameQuality = getEnvInt("MCIO_FRAME_QUALITY", DEFAULT_FRAME_QUALITY);
        frameQuality = Math.clamp(frameQuality, 1, 100);

        // The default depends on the mode. In sync mode we want to go as fast as possible.
        // Async mode can use however Minecraft is configured.
        boolean defaultUnlimitedFPS = switch (mode) {
            case OFF -> false;
            case SYNC -> DEFAULT_UNLIMITED_FPS_SYNC;
            case ASYNC -> DEFAULT_UNLIMITED_FPS_ASYNC;
        };
        unlimitedFPS = getEnvBoolean("MCIO_UNLIMITED_FPS", defaultUnlimitedFPS);

        actionPort = getEnvInt("MCIO_ACTION_PORT", DEFAULT_ACTION_PORT);
        observationPort = getEnvInt("MCIO_OBSERVATION_PORT", DEFAULT_OBSERVATION_PORT);
        hideMinecraftWindow = getEnvBoolean("MCIO_HIDE_WINDOW", DEFAULT_HIDE_MINECRAFT_WINDOW);
        doRetinaHack = getEnvBoolean("MCIO_DO_RETINA_HACK", DEFAULT_DO_RETINA_HACK);

        LOGGER.info("MCIO_MODE={}", mode);
        LOGGER.info("MCIO_FRAME_TYPE={}", frameType);
        if (frameType == MCioFrameType.JPEG) {
            LOGGER.info("MCIO_FRAME_QUALITY={}", frameQuality);
        }
        LOGGER.info("MCIO_UNLIMITED_FPS={}", unlimitedFPS);
        LOGGER.info("MCIO_ACTION_PORT={}", actionPort);
        LOGGER.info("MCIO_OBSERVATION_PORT={}", observationPort);
        LOGGER.info("MCIO_HIDE_WINDOW={}", hideMinecraftWindow);
        LOGGER.info("MCIO_DO_RETINA_HACK={}", doRetinaHack);
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

    private static boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;

        value = value.toLowerCase().trim();
        return value.equals("true") || value.equals("1");
    }

    private static <T extends Enum<T>> T getEnvEnum(String key, T defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(defaultValue.getDeclaringClass(), value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

}