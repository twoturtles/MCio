package net.twoturtles.mixin.client;

import net.minecraft.client.Mouse;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/* Mixins for Mouse class */
public class MouseMixin {
    /* Provide external access to Mouse methods that handle input. */

    @Mixin(Mouse.class)
    public interface OnCursorPosInvoker {
        @Invoker("onCursorPos")
        void invokeOnCursorPos(long window, double x, double y);
    }

    @Mixin(Mouse.class)
    public interface OnMouseButtonInvoker {
        @Invoker("onMouseButton")
        void invokeOnMouseButton(long window, int button, int action, int mods);
    }

}

