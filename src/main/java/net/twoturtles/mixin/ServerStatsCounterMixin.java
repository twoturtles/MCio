package net.twoturtles.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.File;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Player;
import net.twoturtles.MCioConfig;
import net.twoturtles.MCioStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatsCounter.class)
public abstract class ServerStatsCounterMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.ServerStatCounterMixin");

  @Mixin(StatsCounter.class)
  public interface StatsCounterAccessor {
    @Accessor("stats")
    Object2IntMap<Stat<?>> getStats();
  }

  @Mixin(ServerStatsCounter.class)
  public interface asStringInvoker {
    @Invoker("toJson")
    public String invokeToJson();
  }

  @Inject(method = "setValue", at = @At("HEAD"))
  private void injectSetValueHead(Player player, Stat<?> stat, int value, CallbackInfo ci) {
    MCioStats.getInstance().updateStats((ServerStatsCounter) (Object) this, stat, value);
  }

  /* This targets the ServerStatCounter constructor. This needs to run at the start, but you
   * can't target HEAD of init. Instead, targeting the first assignment.
   * XXX Seems brittle
   */
  @Inject(
      method = "<init>",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/stats/ServerStatsCounter;server:Lnet/minecraft/server/MinecraftServer;",
              shift = At.Shift.AFTER))
  private void onInit(MinecraftServer server, File file, CallbackInfo ci) {
    MCioStats.getInstance().onServerStatHandlerInit((ServerStatsCounter) (Object) this);
  }

  /* Optionally skip the stats load to reset */
  @Inject(method = "parseLocal", at = @At("HEAD"), cancellable = true)
  private void injectParseLocalHead(CallbackInfo ci) {
    if (MCioConfig.getInstance().statsReset) {
      ci.cancel(); // Cancel the parse
    }
  }

  /* Copy stats loaded from JSON to MCioStats */
  @Inject(method = "parseLocal", at = @At("RETURN"))
  private void injectParseLocalReturn(CallbackInfo ci) {
    MCioStats.getInstance()
        .replaceStats(
            (ServerStatsCounter) (Object) this, ((StatsCounterAccessor) (Object) this).getStats());
  }
}
