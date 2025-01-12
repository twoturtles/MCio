package net.twoturtles;

public class MCioConfig {
    // Non-configurable constants
    public static final String KEY_CATEGORY = "MCio";
    public static final int MCIO_PROTOCOL_VERSION = 2;
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_ACTION_PORT = 4001; // For receiving 4ctions
    public static final int DEFAULT_OBSERVATION_PORT = 8001;    // For sending 8bservations

    // Configurable state
    public Mode mode;
    public int actionPort;
    public int observationPort;

    // Singleton instance
    private static final MCioConfig INSTANCE = new MCioConfig();

    public static MCioConfig getInstance() {
        return INSTANCE;
    }

    private MCioConfig() {
        mode = Mode.fromEnv();
        actionPort = getEnvInt("MCIO_ACTION_PORT", DEFAULT_ACTION_PORT);
        observationPort = getEnvInt("MCIO_OBSERVATION_PORT", DEFAULT_OBSERVATION_PORT);
    }

    // Mode enum
    public enum Mode {
        OFF, SYNC, ASYNC;

        public static final Mode DEFAULT = ASYNC;

        public static Mode fromEnv() {
            String value = System.getenv("MCIO_MODE");
            if (value == null) return DEFAULT;
            try {
                return Mode.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DEFAULT;
            }
        }
    }

    // Helper method for parsing environment variables
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}