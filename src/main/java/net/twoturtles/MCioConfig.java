package net.twoturtles;

// MCio Config class - command line, env, disk (TODO)
public class MCioConfig {
    public MCioDef.Mode mode;

    public static MCioConfig getInstance() {
        return LazyHolder.INSTANCE;
    }

    // Lazy initialization holder class
    private static class LazyHolder {
        static final MCioConfig INSTANCE = new MCioConfig();
    }

    private MCioConfig() {
        loadDefaults();
        loadFromDisk();
        loadFromEnv();
        loadFromCommandLine();
    }

    private void loadDefaults() {
        mode = MCioDef.Mode.ASYNC;
    }

    // TODO
    private void loadFromDisk() { }
    private void loadFromEnv() {
        mode = MCioDef.Mode.fromString(System.getenv("MCIO_MODE"));
    }
    // TODO
    private void loadFromCommandLine() { }


    public void save() {
        // Save config to disk
    }

    public void reset() {
        loadDefaults();
        save();
    }
}
