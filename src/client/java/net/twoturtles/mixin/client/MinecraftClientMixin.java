package net.twoturtles.mixin.client;

import net.minecraft.client.MinecraftClient;
import com.mojang.logging.LogUtils;
import net.twoturtles.MCioClientAsync;
import org.slf4j.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Unique
    private final Logger LOGGER = LogUtils.getLogger();
    @Shadow
    private boolean windowFocused;

    @Inject(method = "onWindowFocusChanged(Z)V", at = @At("HEAD"), cancellable = true)
    private void onWindowFocusChanged(boolean focused, CallbackInfo ci) {
        // Store the true value of windowFocused in the MCioClientAsync.
        MCioClientAsync.windowFocused = focused;
        // Keep MinecraftClient's copy always true
        windowFocused = true;
        // Cancel the original method to prevent overwriting
        ci.cancel();
    }
}
