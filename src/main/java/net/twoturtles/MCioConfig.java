package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.util.Arrays;

/* XXX Refactor */

public class MCioConfig {
    /**
     * Load config from System Properties or environment variables.
     * The priority is System Property > Env var > default
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum MCioMode {
        OFF, SYNC, ASYNC
    }

    public enum MCioFrameType {
        RAW
    }

    public enum MCioAsyncObsTrigger {
        TICK, FRAME
    }

    // Constants
    public static final int MCIO_PROTOCOL_VERSION = 4;
    public static final String KEY_CATEGORY = "MCio";
    public static final String DEFAULT_HOST = "localhost";

    // Configurable
    public MCioMode mode;
    public MCioFrameType frameType;
    public MCioAsyncObsTrigger observationTrigger;
    public boolean unlimitedFPS;   // Disable Minecraft FPS limiting
    public int actionPort;
    public int observationPort;
    public boolean hideMinecraftWindow;
    public boolean retinaHack;
    public boolean syncSpeedTest;
    public boolean mcioExp1;

    // Defaults
    public static final MCioMode DEFAULT_MCIO_MODE = MCioMode.ASYNC;
    public static final MCioFrameType DEFAULT_MCIO_FRAME_TYPE = MCioFrameType.RAW;
    public static final MCioAsyncObsTrigger DEFAULT_ASYNC_OBSERVATION_TRIGGER = MCioAsyncObsTrigger.FRAME;
    public static final boolean DEFAULT_UNLIMITED_FPS_SYNC = true;
    public static final boolean DEFAULT_UNLIMITED_FPS_ASYNC = false;
    public static final int DEFAULT_ACTION_PORT = 4001; // For receiving 4ctions
    public static final int DEFAULT_OBSERVATION_PORT = 8001;    // For sending 8bservations
    public static final boolean DEFAULT_HIDE_MINECRAFT_WINDOW = false;
    public static final boolean DEFAULT_RETINA_HACK = true;  // Disable retina double resolution
    public static final boolean DEFAULT_SYNC_SPEED_TEST = false;
    public static final boolean DEFAULT_MCIO_EXP1 = false;

    // Singleton instance
    private static final MCioConfig INSTANCE = new MCioConfig();

    public static MCioConfig getInstance() {
        return INSTANCE;
    }

    private MCioConfig() {
        if (getBoolean("MCIO_HELP", false)) {
            PrintStream stdout = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
            stdout.println(getHelp());
            System.exit(0);
        }

        mode = getEnum("MCIO_MODE", DEFAULT_MCIO_MODE);
        frameType = getEnum("MCIO_FRAME_TYPE", DEFAULT_MCIO_FRAME_TYPE);
        observationTrigger = getEnum("MCIO_ASYNC_OBSERVATION_TRIGGER", DEFAULT_ASYNC_OBSERVATION_TRIGGER);

        // The default depends on the mode. In sync mode we want to go as fast as possible.
        // Async mode can use however Minecraft is configured.
        boolean defaultUnlimitedFPS = switch (mode) {
            case OFF -> false;
            case SYNC -> DEFAULT_UNLIMITED_FPS_SYNC;
            case ASYNC -> DEFAULT_UNLIMITED_FPS_ASYNC;
        };
        unlimitedFPS = getBoolean("MCIO_UNLIMITED_FPS", defaultUnlimitedFPS);

        actionPort = getInt("MCIO_ACTION_PORT", DEFAULT_ACTION_PORT);
        observationPort = getInt("MCIO_OBSERVATION_PORT", DEFAULT_OBSERVATION_PORT);
        hideMinecraftWindow = getBoolean("MCIO_HIDE_WINDOW", DEFAULT_HIDE_MINECRAFT_WINDOW);
        retinaHack = getBoolean("MCIO_DO_RETINA_HACK", DEFAULT_RETINA_HACK);
        syncSpeedTest = getBoolean("MCIO_SYNC_SPEED_TEST", DEFAULT_SYNC_SPEED_TEST);
        mcioExp1 = getBoolean("MCIO_EXP1", DEFAULT_MCIO_EXP1);

        LOGGER.info("MCIO_MODE={}", mode);
        LOGGER.info("MCIO_FRAME_TYPE={}", frameType);
        LOGGER.info("MCIO_ASYNC_OBSERVATION_TRIGGER={}", observationTrigger);
        LOGGER.info("MCIO_UNLIMITED_FPS={}", unlimitedFPS);
        LOGGER.info("MCIO_ACTION_PORT={}", actionPort);
        LOGGER.info("MCIO_OBSERVATION_PORT={}", observationPort);
        LOGGER.info("MCIO_HIDE_WINDOW={}", hideMinecraftWindow);
        LOGGER.info("MCIO_RETINA_HACK={}", retinaHack);
        LOGGER.info("MCIO_SYNC_SPEED_TEST={}", syncSpeedTest);
        LOGGER.info("MCIO_EXP1={}", mcioExp1);
    }

    // Helper methods for parsing config values from system properties or env vars
    private static int getInt(String key, int defaultValue) {
        String value = getConfigValue(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid config number: key={} value={}", key, value);
            System.exit(1);
            return defaultValue;
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        if (value == null) return defaultValue;

        value = value.toLowerCase().trim();
        return value.equals("true") || value.equals("1");
    }

    private static <T extends Enum<T>> T getEnum(String key, T defaultValue) {
        String value = getConfigValue(key);
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(defaultValue.getDeclaringClass(), value.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid config enum value: key={} value={}", key, value);
            System.exit(1);
            return defaultValue;
        }
    }

    private static String getConfigValue(String key) {
        // Priority: System Property > Environment Variable > null
        return System.getProperty(key, System.getenv(key));
    }

    public static String getHelp() {
        return """
                
                MCio Configuration Options
                ==========================
                These can be set by environment variable (MCIO_MODE=SYNC) or
                Java system property (-DMCIO_MODE=SYNC)
                
                General Options:
                  MCIO_HELP                      [boolean] Default: false
                    Show this help message and exit
                
                  MCIO_MODE                      [%s] Default: %s
                    Set the operation mode
                
                Communication Options:
                  MCIO_OBSERVATION_PORT          [int] Default: %d
                    Port for sending observations
                
                  MCIO_ACTION_PORT               [int] Default: %d
                    Port for receiving actions
                
                Display Options:
                  MCIO_HIDE_WINDOW               [boolean] Default: %b
                    Hide the Minecraft window
                
                  MCIO_DO_RETINA_HACK            [boolean] Default: %b
                    Disable retina double resolution
                
                Performance Options:
                  MCIO_UNLIMITED_FPS             [boolean] Default: SYNC=%b, ASYNC=%b
                    Disable Minecraft FPS limiting
                    Note: default depends on MCIO_MODE setting
                
                  __GLX_VENDOR_LIBRARY_NAME
                    Use to enable a gpu in headless mode on Linux.
                    Possible values: nvidia, amd, mesa
                
                  MCIO_SYNC_SPEED_TEST           [boolean] Default: %b
                    Enable sync mode speed testing
                
                Advanced Options:
                  MCIO_ASYNC_OBSERVATION_TRIGGER [%s] Default: %s
                    Trigger method for async observations
                
                  MCIO_EXP1                      [boolean] Default: %b
                    Enable experimental feature 1
                
                  MCIO_FRAME_TYPE                [%s] Default: %s
                    Set the frame type format
                
                """.formatted(
                Arrays.toString(MCioMode.values()),
                DEFAULT_MCIO_MODE,
                DEFAULT_OBSERVATION_PORT,
                DEFAULT_ACTION_PORT,
                DEFAULT_HIDE_MINECRAFT_WINDOW,
                DEFAULT_RETINA_HACK,
                DEFAULT_UNLIMITED_FPS_SYNC,
                DEFAULT_UNLIMITED_FPS_ASYNC,
                DEFAULT_SYNC_SPEED_TEST,
                Arrays.toString(MCioAsyncObsTrigger.values()),
                DEFAULT_ASYNC_OBSERVATION_TRIGGER,
                DEFAULT_MCIO_EXP1,
                Arrays.toString(MCioFrameType.values()),
                DEFAULT_MCIO_FRAME_TYPE
        );
    }
}