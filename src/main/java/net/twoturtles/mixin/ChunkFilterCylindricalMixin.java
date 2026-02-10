// package net.minecraft.server.network;
package net.twoturtles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.twoturtles.MCioChunks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkTrackingView.Positioned.class)
public abstract class ChunkFilterCylindricalMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.main.ChunkFilterCylindricalMixin");

  /* The recommended use of Shadow is to declare the class and methods abstract */
  @Shadow
  abstract int minX();

  @Shadow
  abstract int maxX();

  @Shadow
  abstract int minZ();

  @Shadow
  abstract int maxZ();

  @Inject(method = "forEach", at = @At("HEAD"))
  private void beforeForEachStart(Consumer<ChunkPos> consumer, CallbackInfo ci) {
    ChunkTrackingView.Positioned cyl = ((ChunkTrackingView.Positioned) (Object) this);
    LOGGER.info(
        "SelectChunks Started center={} range-x={}:{} range-z={}:{} viewDistance={}",
        cyl.center(),
        minX(),
        maxX(),
        minZ(),
        maxZ(),
        cyl.viewDistance());
    MCioChunks.getInstance().selectStart();
  }

  @Inject(
      method = "forEach",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
  private void beforeAcceptCall(
      Consumer<ChunkPos> consumer,
      CallbackInfo ci,
      @Local(ordinal = 0) int i,
      @Local(ordinal = 1) int j) {
    MCioChunks.getInstance().selectTrack(i, j);
  }

  @Inject(method = "forEach", at = @At("TAIL"))
  private void afterForEachEnd(Consumer<ChunkPos> consumer, CallbackInfo ci) {
    MCioChunks.getInstance().selectEnd();
  }
}
