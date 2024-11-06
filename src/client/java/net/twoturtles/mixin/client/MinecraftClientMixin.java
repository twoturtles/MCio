package net.twoturtles.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

/* Mixins into the MinecraftClient class */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    private boolean windowFocused;

    /**
     * @author TwoTurtles
     * @reason Always keep windowFocused true. Mouse inputs are ignored when it's false.
     * This also prevents pausing to the menu when you change focus.
     * Apparently overwrites must have a javadoc with author and reason.
     */
    @Overwrite
    public void onWindowFocusChanged(boolean focused) {
        this.windowFocused = true;
    }
}
