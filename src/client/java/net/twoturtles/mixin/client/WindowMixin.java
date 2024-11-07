package net.twoturtles.mixin.client;

import net.minecraft.client.util.Window;
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

        Window window = (Window)(Object)this;
        int width = window.getWidth();
        int height = window.getHeight();

        ByteBuffer pixelBuffer = MCioFrameCapture.getBuffer(width, height);
        pixelBuffer.clear(); // Reset position to 0

        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);

        /* Add test code here to write frames to a file. */
        test(pixelBuffer);
    }

    private void test(ByteBuffer pixelBuffer) {
        try {
            java.io.File outputDir = new java.io.File("frame_captures");
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }

            String fileName = String.format("frame_%d.raw", System.currentTimeMillis());
            java.io.File outputFile = new java.io.File(outputDir, fileName);

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                pixelBuffer.rewind();  // Make sure we're at the start of the buffer
                byte[] bytes = new byte[pixelBuffer.remaining()];
                pixelBuffer.get(bytes);
                fos.write(bytes);
            }
        } catch (java.io.IOException e) {
            System.err.println("Failed to write frame: " + e.getMessage());
        }
    }
}
