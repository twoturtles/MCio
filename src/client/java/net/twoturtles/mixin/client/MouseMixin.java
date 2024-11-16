package net.twoturtles.mixin.client;

import net.twoturtles.MCioController;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Mouse;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.twoturtles.MouseMixinInterface;

// Mixins for Mouse class
@Mixin(Mouse.class)
public class MouseMixin implements MouseMixinInterface {
    @Unique
    private final Logger LOGGER = LogUtils.getLogger();
    @Unique
    private boolean isAgentMovement = false;

    // Public accessors for the private x and y fields
    @Mixin(Mouse.class)
    public interface MouseAccessor {
        @Accessor("x")
        double getX();
        @Accessor("y")
        double getY();
    }

    // Access to onMouseButton for the agent.
    @Mixin(Mouse.class)
    public interface OnMouseButtonInvoker {
        @Invoker("onMouseButton")
        void invokeOnMouseButton(long window, int button, int action, int mods);
    }

    /* Everything below is a convoluted path to allow the agent to update the cursor position.
     * The call path for the agent is:
     * MouseMixinInterface.onCursorPosAgent -> MouseMixin.OnCursorPosAgent ->
     * MouseMixin.invokeOnCursorPos -> Mouse.onCursorPos
     */

    // Injects code at the start of the onCursorPos method
    // Block physical mouse movement when the window isn't focused, but still allow the
    // agent to move the cursor. Normally the cursor position still updates when unfocused
    // if it's on the Minecraft window.
    @Inject(method = "onCursorPos(JDD)V", at = @At("HEAD"), cancellable = true)
    private void onCursorPosStart(long window, double x, double y, CallbackInfo ci) {
        if (!isAgentMovement && !MCioController.windowFocused) {
            // Physical mouse movement but window isn't focused. Cancel movement.
            ci.cancel();
        }
    }
    // Used by the agent to move the cursor when the window isn't focused.
    // This implements a method in MouseMixinInterface. You can't create a new method in a Mixin,
    // but adding it via an interface works.
    @Override
    public void onCursorPosAgent(long window, double x, double y) {
        LOGGER.warn("onCursorPosAgent");
        isAgentMovement = true;
        try {
            ((OnCursorPosInvoker) this).invokeOnCursorPos(window, x, y);
        } finally {
            isAgentMovement = false;
        }
    }
    @Mixin(Mouse.class)
    public interface OnCursorPosInvoker {
        @Invoker("onCursorPos")
        void invokeOnCursorPos(long window, double x, double y);
    }

}
