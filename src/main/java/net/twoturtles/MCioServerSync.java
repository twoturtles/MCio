package net.twoturtles;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

class MCioServerSync {
  private final Logger LOGGER = LogUtils.getLogger();
  private final MCioSyncUtil syncUtil = MCioSyncUtil.getInstance();

  public MCioServerSync(MCioConfig config) {
    ServerLifecycleEvents.SERVER_STARTED.register(this::init);
    ServerTickEvents.START_SERVER_TICK.register(this::startTickCB);
    ServerTickEvents.END_SERVER_TICK.register(this::endTickCB);
  }

  void init(MinecraftServer server) {
    syncUtil.serverInit(server);
  }

  void startTickCB(MinecraftServer server) {
    syncUtil.serverStartTick();
  }

  void endTickCB(MinecraftServer server) {
    syncUtil.serverEndTick();
  }

  void stop() {}
}
