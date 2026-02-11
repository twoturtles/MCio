package net.twoturtles.mixin.client;

import static org.lwjgl.opengl.GL11.*;

import com.mojang.blaze3d.platform.Window;
import java.nio.ByteBuffer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.twoturtles.MCioConfig;
import net.twoturtles.MCioFrameCapture;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
  @Unique
  private static final Logger LOGGER =
      LoggerFactory.getLogger("net.twoturtles.mixin.client.WindowMixin");

  @Unique private static boolean checkFrameSize = false;

  // This captures frames and stores them to MCioFrameCapture. This plugs in to the Minecraft
  // updateDisplay (frame buffer swap) method so the frame is ready to go when it's captured.
  // ObservationHandler picks up the most recent frame at the end of every tick */
  @Inject(method = "updateDisplay", at = @At("HEAD"))
  private void beforeUpdateDisplay(CallbackInfo ci) {
    MCioFrameCapture frameCapture = MCioFrameCapture.getInstance();
    frameCapture.incrementFrameSequence();

    if (!frameCapture.isEnabled()) return;

    doCapture(frameCapture);
  }

  @Unique
  private void doCapture(MCioFrameCapture frameCapture) {
    Window window = (Window) (Object) this;
    int width = window.getWidth();
    int height = window.getHeight();

    ByteBuffer pixelBuffer =
        ByteBuffer.allocateDirect(width * height * frameCapture.BYTES_PER_PIXEL);
    pixelBuffer.clear(); // Reset position to 0

    // Need alignment set to 1 to properly read frame sizes that are not multiples of 4.
    int[] alignment = new int[1];
    glGetIntegerv(GL_PACK_ALIGNMENT, alignment);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadBuffer(GL_BACK);
    glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixelBuffer);
    // Reset alignment to previous value
    glPixelStorei(GL_PACK_ALIGNMENT, alignment[0]);

    frameCapture.capture(pixelBuffer, width, height);
  }

  // Intercepts the call to glfwDefaultWindowHints() so we can make modifications to the hints.
  @Shadow @Final private long window;

  @Redirect(
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"),
      method = "<init>",
      remap = false)
  private void onDefaultWindowHints() {
    // First, set defaults.
    GLFW.glfwDefaultWindowHints();

    MCioConfig config = MCioConfig.getInstance();
    if (config.hideMinecraftWindow) {
      GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
    if (config.retinaHack) {
      retinaHack();
    }
  }

  // Based on
  // https://github.com/FlashyReese/sodium-extra-fabric/blob/1.21/dev/common/src/main/java/me/flashyreese/mods/sodiumextra/mixin/reduce_resolution_on_mac/MixinWindow.java
  // Disable double sized frame buffer on retina displays.
  @Unique
  private void retinaHack() {
    if (Minecraft.ON_OSX) {
      // This makes it so windows aren't double resolution on retina displays
      LOGGER.info("RETINA-FRAMEBUFFER-DISABLE");
      GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER /* 143361 */, GLFW.GLFW_FALSE);
      checkFrameSize = true;
    }

    /*
    The retina flag above doesn't quite work. The frame buffer ends up being twice the size of the window.
    I noticed that resizing the window fixes this. This hack does little resizes to the window until
    the frame buffer matches. It seems to take multiple calls, so do it until it works. Maybe some
    timing issue. This has to be done late enough in initialization that the frame buffer exists.
    Triggering off client ticks seems safe.
    Possibly related to this https://github.com/glfw/glfw/issues/1968
    */
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (!checkFrameSize) {
            return;
          }
          int[] winWidth = new int[1];
          int[] winHeight = new int[1];
          int[] frameWidth = new int[1];
          int[] frameHeight = new int[1];

          GLFW.glfwGetFramebufferSize(window, frameWidth, frameHeight);
          GLFW.glfwGetWindowSize(window, winWidth, winHeight);
          LOGGER.debug(
              "RETINA-HACK frame={},{} win={},{}",
              frameWidth[0],
              frameHeight[0],
              winWidth[0],
              winHeight[0]);
          GLFW.glfwSetWindowSize(window, winWidth[0] - 1, winHeight[0] - 1);
          GLFW.glfwSetWindowSize(window, winWidth[0], winHeight[0]);
          if (frameWidth[0] == winWidth[0] || frameHeight[0] == winHeight[0]) {
            LOGGER.info("RETINA-HACK-SUCCESS");
            checkFrameSize = false;
          }
        });
  }
}
