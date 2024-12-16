package net.twoturtles;

public class MCioDef {
    public static final String KEY_CATEGORY = "MCio";

    public enum Mode {
        OFF("off"),
        SYNC("sync"),
        ASYNC("async");

        private final String value;
        public static final Mode DEFAULT = ASYNC;

        Mode(String value) {
            this.value = value;
        }

        public static Mode fromString(String str) {
            if (str == null) return DEFAULT;
            for (Mode mode : Mode.values()) {
                if (mode.value.equalsIgnoreCase(str)) {
                    return mode;
                }
            }
            return DEFAULT;
        }
    }

}
