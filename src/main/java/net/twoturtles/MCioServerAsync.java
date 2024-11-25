package net.twoturtles;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

class MCioServerAsync {
    private final Logger LOGGER = LogUtils.getLogger();
    private MCioConfig config;

    public MCioServerAsync(MCioConfig config) {
        this.config = config;
    }

    void stop() { }
}
