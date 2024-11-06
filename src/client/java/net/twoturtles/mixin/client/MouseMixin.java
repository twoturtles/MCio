package net.twoturtles.mixin.client;

import net.minecraft.client.Mouse;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/* Mixins for Mouse class */
public class MouseMixin {
    /* onCursorPos is private. Use mixin to provide a way to invoke externally. */
    @Mixin(Mouse.class)
    public interface OnCursorPosInvoker {
        @Invoker("onCursorPos")
        void invokeOnCursorPos(long window, double x, double y);
    }
}

