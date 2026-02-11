package net.twoturtles.mixin.client;

import net.minecraft.client.Minecraft;
import net.twoturtles.*;
import net.twoturtles.MCioConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.client.MinecraftMixin");

  @Shadow private boolean windowActive;

  @Inject(method = "setWindowActive(Z)V", at = @At("HEAD"), cancellable = true)
  private void onWindowFocusChanged(boolean focused, CallbackInfo ci) {
    // Store the true value of windowFocused in the MCioClient for access by MouseMixin
    // XXX There has to be a cleaner way to do this
    MCioClient.MCioWindowFocused = focused;
    // Keep MinecraftClient's copy always true
    windowActive = true;
    // Cancel the original method to prevent overwriting
    ci.cancel();
  }

  // For Mode.SYNC only -
  // Targeting "i": int i = this.renderTickCounter.beginRenderTick(Util.getMeasuringTimeMs(), tick);
  // Seems brittle. This targets the first int assigned in the method.
  // This is the number of ticks to take. Normally 0 or 1, but can be higher (to catch up?).
  // Make it always 1 so we tick every frame, but not more than 1 so we generate a frame every tick.
  @ModifyVariable(method = "runTick(Z)V", at = @At("STORE"), ordinal = 0)
  private int injected(int i) {
    if (MCioConfig.getInstance().mode == MCioConfig.MCioMode.SYNC) {
      if (i > 1) {
        LOGGER.debug("Reducing i {} -> 1", i);
      }
      return 1;
    } else {
      // Not SYNC, use original value
      return i;
    }
  }
}
