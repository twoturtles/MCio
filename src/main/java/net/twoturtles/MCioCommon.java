package net.twoturtles;

public class MCioCommon {
}
enum MCioMode {
    SYNC,
    ASYNC
}

class MCioConfig {
    MCioMode mode;
    MCioConfig() {
        // XXX Load from disk
        mode = MCioMode.ASYNC;
    }
}

class MCio_Const {
    public static final String KEY_CATEGORY = "MCio";
}

