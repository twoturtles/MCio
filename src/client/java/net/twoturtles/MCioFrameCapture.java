package net.twoturtles;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.twoturtles.util.TrackFPS;

/* Interface and state storage for WindowMixin:beforeSwap */
public final class MCioFrameCapture {
    public static final int CAPTURE_EVERY_N_FRAMES = 2;
    public static final int BYTES_PER_PIXEL = 3;    // GL_RGB

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TrackFPS frameFPS = new TrackFPS("FRAME");
    private static final TrackFPS captureFPS = new TrackFPS("CAPTURE");

    private static boolean enabled = false;
    private static int frameCount = 0;
    private static MCioFrame lastCapturedFrame = null;

    public record MCioFrame(
            int frame_count,
            int width,
            int height,
            int bytes_per_pixel,
            ByteBuffer frame
    ) { }

    private MCioFrameCapture() {
        throw new AssertionError("Singleton: do not instantiate");
    }

    public static void setLastFrame(MCioFrame frame) {
        captureFPS.count();
        lastCapturedFrame = frame;
    }
    public static MCioFrame getLastCapturedFrame() {
        return lastCapturedFrame;
    }

    public static void setEnabled(boolean enabled_val) { enabled = enabled_val; }
    public static boolean isEnabled() { return enabled; }

    public static void incrementFrameCount() { frameCount++; }
    public static int getFrameCount() { return frameCount; }

    public static boolean shouldCaptureFrame() {
        frameFPS.count();
        return frameCount % CAPTURE_EVERY_N_FRAMES == 0;
    }
}

/* Provides hot key to save frames to files without printing a message to the screen. */
class MCioFrameSave {
    private final Logger LOGGER = LogUtils.getLogger();
    private KeyBinding captureKey;
    private int frameCount = 0;

    public void initialize() {
        LOGGER.info("Init");

        // Register the keybinding (default to C (capture))
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "MCioFrameSave",
                GLFW.GLFW_KEY_C,
                MCIO_CONST.KEY_CATEGORY
        ));

        // Register the tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureKey.wasPressed() && client.world != null) {
                doCapture(client);
                doCapture2(client);
            }
        });
    }

    private void doCapture(MinecraftClient client) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = String.format("frame_%s_%d.png", timestamp, frameCount++);
        LOGGER.info("Captured frame: {}", fileName);

        // Capture the frame using Minecraft's screenshot recorder
        ScreenshotRecorder.saveScreenshot(
                client.runDirectory,
                fileName,
                client.getFramebuffer(),
                (message) -> {}       // No message to the UI
        );

        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getLastCapturedFrame();
    }

    private void doCapture2(MinecraftClient client) {
        try {
            java.io.File outputDir = new java.io.File("frame_captures");
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }

            /* frameCount won't be accurate */
            String fileName = String.format("frame_%d.raw", MCioFrameCapture.getFrameCount());
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

