package net.twoturtles;

public class MCioCommon {
}
enum MCioMode {
    SYNC,
    ASYNC
}

class MCioConfig {
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
        mode = MCioMode.ASYNC;
    }
}

class MCioConst {
    public static final String KEY_CATEGORY = "MCio";
}

