package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

class MCioServerAsync {
  private final Logger LOGGER = LogUtils.getLogger();
  private MCioConfig config;

  public MCioServerAsync(MCioConfig config) {
    this.config = config;
  }

  void stop() {}
}
