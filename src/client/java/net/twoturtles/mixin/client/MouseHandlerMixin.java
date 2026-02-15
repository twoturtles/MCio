package net.twoturtles.mixin.client;

import net.minecraft.client.MouseHandler;
import net.twoturtles.MCioClient;
import net.twoturtles.MouseHandlerMixinInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixins for Mouse class
@Mixin(MouseHandler.class)
public class MouseHandlerMixin implements MouseHandlerMixinInterface {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.client.MouseHandlerMixin");

  @Unique private boolean isAgentMovement = false;

  // Public setters for the private x and y fields (there are already public getters).
  @Mixin(MouseHandler.class)
  public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void setXpos(double x);

    @Accessor("ypos")
    void setYpos(double y);

    @Accessor("ignoreFirstMove")
    void setIgnoreFirstMove(boolean ignore);
  }

  // Access to onPress for the agent. (Mouse button press)
  @Mixin(MouseHandler.class)
  public interface OnPressInvoker {
    @Invoker("onPress")
    void invokeOnPress(long window, int button, int action, int mods);
  }

  /* Everything below is a convoluted path to allow the agent to update the cursor position.
   * The call path for the agent is:
   * MouseHandlerMixinInterface.onMoveAgent$Mixin -> MouseHandlerMixin.OnMoveAgent$Mixin ->
   * MouseHandlerMixin.invokeOnMove -> MouseHandler.onMove
   */

  // Injects code at the start of the onMove method
  // Block physical mouse movement when the window isn't focused, but still allow the
  // agent to move the cursor. Normally the cursor position still updates when unfocused
  // if it's on the Minecraft window.
  @Inject(method = "onMove(JDD)V", at = @At("HEAD"), cancellable = true)
  private void onCursorPosStart(long window, double x, double y, CallbackInfo ci) {
    if (!isAgentMovement && !MCioClient.MCioWindowFocused) {
      // Physical mouse movement but window isn't focused. Cancel movement.
      ci.cancel();
    }
  }

  // Used by the agent to move the cursor when the window isn't focused.
  // This implements a method in MouseHandlerMixinInterface. You can't create a new method in a
  // Mixin, but adding it via an interface works.
  @Override
  public void onMoveAgent$Mixin(long window, double x, double y) {
    isAgentMovement = true;
    try {
      // Clear ignoreFirstMove so agent movements are never silently absorbed.
      // Minecraft sets this flag in grabMouse() when a screen closes (e.g. death screen),
      // causing the next onMove() to only sync xpos/ypos without accumulating any camera delta.
      ((MouseHandlerAccessor) this).setIgnoreFirstMove(false);
      ((OnMoveInvoker) this).invokeOnMove(window, x, y);
    } finally {
      isAgentMovement = false;
    }
  }

  @Mixin(MouseHandler.class)
  public interface OnMoveInvoker {
    @Invoker("onMove")
    void invokeOnMove(long window, double x, double y);
  }
}
