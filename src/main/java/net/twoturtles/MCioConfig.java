package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

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
    public static final int MCIO_PROTOCOL_VERSION = 6;
    public static final String KEY_CATEGORY = "MCio";
    public static final String DEFAULT_HOST = "localhost";
    public static final String MCIO_TYPE = "__mcio_type__";

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
    public boolean mcioPreloadChunks;
    public int skin;
    public boolean openToLan;
    public int openLanToPort;
    public GameMode openToLanMode;
    public boolean statsReset;

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
    public static final boolean DEFAULT_MCIO_PRELOAD_CHUNKS = true;
    public static final int DEFAULT_MCIO_SKIN = 15; // wide/steve
    public static final boolean DEFAULT_OPEN_TO_LAN = false;
    public static final int DEFAULT_OPEN_TO_LAN_PORT = 12001;
    public static final GameMode DEFAULT_OPEN_TO_LAN_MODE = GameMode.SPECTATOR;
    public static final boolean DEFAULT_STATS_RESET = true;

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
        if (getBoolean("MCIO_HELP_SKINS", false)) {
            printSkinHelp = true;
        }
        if (getBoolean("MCIO_HELP_STATS", false)) {
            // Triggers when the player connects to the server
            MCioStats.getInstance().setDoFullStats();
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
        mcioPreloadChunks = getBoolean("MCIO_PRELOAD_CHUNKS", DEFAULT_MCIO_PRELOAD_CHUNKS);
        skin = getInt("MCIO_SKIN", DEFAULT_MCIO_SKIN);
        openToLan = getBoolean("MCIO_OPEN_TO_LAN", DEFAULT_OPEN_TO_LAN);
        openLanToPort = getInt("MCIO_OPEN_TO_LAN_PORT", DEFAULT_OPEN_TO_LAN_PORT);
        openToLanMode = getEnum("MCIO_OPEN_TO_LAN_MODE", DEFAULT_OPEN_TO_LAN_MODE);
        statsReset = getBoolean("MCIO_STATS_RESET", DEFAULT_STATS_RESET);

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
        LOGGER.info("MCIO_PRELOAD_CHUNKS={}", mcioPreloadChunks);
        LOGGER.info("MCIO_SKIN={}", skin);
        LOGGER.info("MCIO_OPEN_TO_LAN={}", openToLan);
        LOGGER.info("MCIO_OPEN_TO_LAN_PORT={}", openLanToPort);
        LOGGER.info("MCIO_OPEN_TO_LAN_MODE={}", openToLanMode);
        LOGGER.info("MCIO_STATS_RESET={}", statsReset);
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

    // Hack to get skin names from the client namespace to main namespace.
    private boolean printSkinHelp = false;
    // Triggered when the client starts.
    public void printSkins(List<String> skins) {
        if (printSkinHelp) {
            PrintStream stdout = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
            stdout.printf("\n\nDefault Skins:\n");
            for (int i = 0; i < skins.size(); i++) {
                stdout.printf("%2d %s\n", i, skins.get(i));
            }
            System.exit(0);
        }
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
                
                  MCIO_MODE                      %s Default: %s
                    Set the operation mode
                
                Communication Options:
                  MCIO_OBSERVATION_PORT          [int] Default: %d
                    Port for sending observations
                
                  MCIO_ACTION_PORT               [int] Default: %d
                    Port for receiving actions
                
                  MCIO_OPEN_TO_LAN               [boolean] Default: %b
                    Enable LAN multiplayer
                
                  MCIO_OPEN_TO_LAN_PORT          [int] Default: %d
                    Server listen port
                
                  MCIO_OPEN_TO_LAN_MODE          %s Default: %s
                    Initial multiplayer mode
                
                Display Options:
                  MCIO_HIDE_WINDOW               [boolean] Default: %b
                    Hide the Minecraft window
                
                  MCIO_DO_RETINA_HACK            [boolean] Default: %b
                    Disable retina double resolution
                
                Performance Options:
                  MCIO_UNLIMITED_FPS             [boolean] Default: SYNC=%b, ASYNC=%b
                    Disable Minecraft FPS limiting
                    Note: default depends on MCIO_MODE setting
                
                  __GLX_VENDOR_LIBRARY_NAME      [nvidia, amd, mesa]
                    Use to enable a gpu in headless mode on Linux.
                
                  MCIO_PRELOAD_CHUNKS           [boolean] Default: %b
                    Pre-load the initial chunks around the player.
                    Only applies in SYNC mode.
                
                  MCIO_SYNC_SPEED_TEST           [boolean] Default: %b
                    Enable sync mode speed testing
                
                Other Options:
                  MCIO_SKIN                      [int] Default: %d
                    Skin selection
                
                  MCIO_STATS_RESET               [boolean] Default: %b
                    Reset all stats to zero on player connect.
                    This is done by skipping the stats json load.
                
                  MCIO_HELP_SKINS                [boolean] Default: false
                    List the default skins and exit
                
                  MCIO_HELP_STATS                [boolean] Default: false
                    Generate a sample stats file containing all possible stat entries, then exit
                    The player must enter a world to trigger the generation
                
                Advanced Options:
                  MCIO_ASYNC_OBSERVATION_TRIGGER %s Default: %s
                    Trigger method for async observations
                
                  MCIO_EXP1                      [boolean] Default: %b
                    Enable experimental feature 1
                
                  MCIO_FRAME_TYPE                %s Default: %s
                    Set the frame type format
                
                """.formatted(
                Arrays.toString(MCioMode.values()),
                DEFAULT_MCIO_MODE,
                DEFAULT_OBSERVATION_PORT,
                DEFAULT_ACTION_PORT,
                DEFAULT_OPEN_TO_LAN,
                DEFAULT_OPEN_TO_LAN_PORT,
                Arrays.toString(GameMode.values()),
                DEFAULT_OPEN_TO_LAN_MODE,
                DEFAULT_HIDE_MINECRAFT_WINDOW,
                DEFAULT_RETINA_HACK,
                DEFAULT_UNLIMITED_FPS_SYNC,
                DEFAULT_UNLIMITED_FPS_ASYNC,
                DEFAULT_MCIO_PRELOAD_CHUNKS,
                DEFAULT_SYNC_SPEED_TEST,
                DEFAULT_MCIO_SKIN,
                DEFAULT_STATS_RESET,
                Arrays.toString(MCioAsyncObsTrigger.values()),
                DEFAULT_ASYNC_OBSERVATION_TRIGGER,
                DEFAULT_MCIO_EXP1,
                Arrays.toString(MCioFrameType.values()),
                DEFAULT_MCIO_FRAME_TYPE
        );
    }
}