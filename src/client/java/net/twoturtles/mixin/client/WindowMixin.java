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

        ByteBuffer buffer = MCioFrameCapture.getBuffer(width, height);
        buffer.clear(); // Reset position to 0

        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

    }
}
