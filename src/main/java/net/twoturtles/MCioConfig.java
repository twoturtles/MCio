package net.twoturtles;

public class MCioConfig {
    private static MCioConfig instance;
    MCioMode mode;

    public static MCioConfig getInstance() {
        if (instance == null) {
            instance = new MCioConfig();
        }
        return instance;
    }

    private MCioConfig() {
        // XXX Load from disk
        mode = MCioMode.SYNC;
    }
}

