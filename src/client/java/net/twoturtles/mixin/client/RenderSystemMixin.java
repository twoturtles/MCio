package net.twoturtles.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.twoturtles.MCioConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the call to RenderSystem.limitDisplayFPS based on config This disables both the regular
 * and the inactivity (AFK) FPS limiting
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {
  @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
  private static void disableLimitDisplayFPS(int fps, CallbackInfo ci) {
    if (MCioConfig.getInstance().unlimitedFPS) {
      ci.cancel(); // Don't limit, cancel the call
    }
  }
}
