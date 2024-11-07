package net.twoturtles.mixin.client;

import net.minecraft.client.util.Window;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

import net.twoturtles.MCioFrameCapture;

@Mixin(Window.class)
public class WindowMixin {
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

}
