package net.twoturtles.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import org.lwjgl.glfw.GLFW;
import static org.lwjgl.opengl.GL11.*;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.twoturtles.MCioFrameCapture;

@Mixin(Window.class)
public class WindowMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();
    @Unique
    private static boolean checkFrameSize = true;
    @Unique
    private static boolean doRetinaHack = true;

    @Inject(method = "swapBuffers", at = @At("HEAD"))
    private void beforeSwap(CallbackInfo ci) {
        if (!MCioFrameCapture.isEnabled()) return;
        MCioFrameCapture.incrementFrameCount();
        if (!MCioFrameCapture.shouldCaptureFrame()) {
            return;
        }
        int frameCount = MCioFrameCapture.getFrameCount();

        Window window = (Window)(Object)this;
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();

        /* XXX ring buffer or swap frames */
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(width * height * MCioFrameCapture.BYTES_PER_PIXEL);
        pixelBuffer.clear(); // Reset position to 0
        MCioFrameCapture.MCioFrame frame = new MCioFrameCapture.MCioFrame(
                frameCount, width, height, MCioFrameCapture.BYTES_PER_PIXEL, pixelBuffer);

        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixelBuffer);

        /* Bad synchronization, but works for now. Once this is handed off, this thread won't touch it again. */
        MCioFrameCapture.setLastFrame(frame);
    }

    // Based on https://github.com/FlashyReese/sodium-extra-fabric/blob/1.21/dev/common/src/main/java/me/flashyreese/mods/sodiumextra/mixin/reduce_resolution_on_mac/MixinWindow.java
    // Disable double sized frame buffer on retina displays.
    @Shadow @Final private long handle;
    @Redirect(at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"),
            method = "<init>", remap = false)
    private void onDefaultWindowHints() {
        if (!doRetinaHack) {
            return;
        }
        GLFW.glfwDefaultWindowHints();
        if (MinecraftClient.IS_SYSTEM_MAC) {
            LOGGER.info("RETINA-FRAME-BUFFER-DISABLE");
            // 143361
            GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW.GLFW_FALSE);
        }

        // The retina flag above doesn't quite work. The frame buffer ends up being twice the size of the window.
        // I noticed that resizing the window fixes this. This hack does little resizes to the window until
        // the frame buffer matches. It seems to take multiple calls, so do it until it works. Maybe some
        // timing issue. This has to be done late enough in initialization that the frame buffer exists.
        // Triggering off client ticks seems safe.
        // Possibly related to this https://github.com/glfw/glfw/issues/1968
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!checkFrameSize) {
                return;
            }
            int[] winWidth = new int[1];
            int[] winHeight = new int[1];
            int[] frameWidth = new int[1];
            int[] frameHeight = new int[1];

            GLFW.glfwGetFramebufferSize(handle, frameWidth, frameHeight);
            GLFW.glfwGetWindowSize(handle, winWidth, winHeight);
            LOGGER.info("RETINA-HACK frame={},{} win={},{}", frameWidth[0], frameHeight[0], winWidth[0], winHeight[0]);
            GLFW.glfwSetWindowSize(handle, winWidth[0] - 1, winHeight[0] - 1);
            GLFW.glfwSetWindowSize(handle, winWidth[0], winHeight[0]);
            if (frameWidth[0] == winWidth[0] || frameHeight[0] == winHeight[0]) {
                LOGGER.info("RETINA-HACK-SUCCESS");
                checkFrameSize = false;
            }
        });
    }

}
