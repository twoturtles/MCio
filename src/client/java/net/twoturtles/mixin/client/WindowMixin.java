package net.twoturtles.mixin.client;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;


import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import static org.lwjgl.opengl.GL11.*;

import net.twoturtles.MCioFrameCapture;

@Mixin(Window.class)
public class WindowMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

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

        /* Bad synchronization. Once this is handed off, this thread won't touch it again. */
        MCioFrameCapture.setLastFrame(frame);
        pixelBuffer = null;
        frame = null;
    }

    /* Based on https://github.com/FlashyReese/sodium-extra-fabric/blob/1.21/dev/common/src/main/java/me/flashyreese/mods/sodiumextra/mixin/reduce_resolution_on_mac/MixinWindow.java */
    // Doesn't quite work.
//    @Shadow
//    private int framebufferWidth;
//
//    @Shadow
//    private int framebufferHeight;
//
//    @Redirect(at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"),
//            method = "<init>", remap = false)
//    private void onDefaultWindowHints() {
//        GLFW.glfwDefaultWindowHints();
//        if (MinecraftClient.IS_SYSTEM_MAC) {
//            GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW.GLFW_FALSE);
//        }
//    }
//
//    @Inject(at = @At(value = "RETURN"), method = "onFramebufferSizeChanged")
//    private void afterUpdateFrameBufferSize(CallbackInfo ci) {
//        // prevents mis-scaled startup screen
//        if (MinecraftClient.IS_SYSTEM_MAC) {
//            framebufferWidth /= 2;
//            framebufferHeight /= 2;
//        }
//    }
//

}
