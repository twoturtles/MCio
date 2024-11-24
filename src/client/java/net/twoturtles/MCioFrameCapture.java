package net.twoturtles;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallback;

/* Interface and state storage for WindowMixin:beforeSwap. beforeSwap does the actual capture
 * and stores the frame here. StateHandler picks up the most recent frame at the end of every tick */
public final class MCioFrameCapture {
    public static final int CAPTURE_EVERY_N_FRAMES = 2;
    public static final int BYTES_PER_PIXEL = 3;    // GL_RGB

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TrackPerSecond frameFPS = new TrackPerSecond("Frames");
    private static final TrackPerSecond captureFPS = new TrackPerSecond("FrameCaptures");

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

    public static void incrementFrameCount() { frameCount++; }
    public static int getFrameCount() { return frameCount; }

    public static boolean shouldCaptureFrame() {
        frameFPS.count();
        return frameCount % CAPTURE_EVERY_N_FRAMES == 0;
    }

    public static MCioFrame getLastCapturedFrame() { return lastCapturedFrame; }

    /* Convert the pixels in the frame to a PNG */
    public static ByteBuffer getFramePNG(MCioFrame frame) {
        frame.frame().rewind();  // Make sure we're at the start of the buffer

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (STBIWriteCallback callback = STBIWriteCallback.create((context, data, size) -> {
            byte[] bytes = new byte[size];
            MemoryUtil.memByteBuffer(data, size).get(bytes);
            outputStream.write(bytes, 0, size);
        })) {
            /* Flip the OpenGL frame */
            STBImageWrite.stbi_flip_vertically_on_write(true);
            boolean success = STBImageWrite.stbi_write_png_to_func(callback, 0L,
                    frame.width(), frame.height(), BYTES_PER_PIXEL, frame.frame(),
                    frame.width() * BYTES_PER_PIXEL
            );
            if (!success) {
                throw new RuntimeException("Failed to write PNG");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error writing PNG: " + e.getMessage(), e);
        }

        return ByteBuffer.wrap(outputStream.toByteArray());
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
                MCio_Const.KEY_CATEGORY
        ));

        // Register the tick event to pick up the key press.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureKey.wasPressed() && client.world != null) {
                doCapturePNG(client);
                // doCaptureMC(client);
                // doCaptureRaw(client);
            }
        });
    }

    /* Write file using Minecraft's built-in screenshot writer, but without printing a message to the screen.
     * These end up in the standard minecraft screenshots dir. */
    private void doCaptureMC(MinecraftClient client) {
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
    }

    /* Write png to frame_captures dir */
    /* for 1280x1280 frames: PNG 1.2M, Raw 4.7M, PNG from PIL: 964K, Minecraft screenshot PNG: 1.4M */
    private void doCapturePNG(MinecraftClient client) {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getLastCapturedFrame();
        frame.frame().rewind();  // Make sure we're at the start of the buffer

        java.io.File outputDir = new java.io.File("frame_captures");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        String fileName = String.format("frame_%d.png", frame.frame_count());
        java.io.File outputFile = new java.io.File(outputDir, fileName);

        STBImageWrite.stbi_flip_vertically_on_write(true);
        STBImageWrite.stbi_write_png(outputFile.getAbsolutePath(), frame.width(), frame.height(),
                3, frame.frame(), frame.width() * 3);
        LOGGER.info("Captured frame: {}", outputFile.getAbsolutePath());
    }

    /* Save raw pixels to file. The OpenGL origin is in the bottom left, so the frames will appear upside down. */
    private void doCaptureRaw(MinecraftClient client) {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getLastCapturedFrame();
        frame.frame().rewind();  // Make sure we're at the start of the buffer

        java.io.File outputDir = new java.io.File("frame_captures");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        String fileName = String.format("frame_%d.raw", frame.frame_count());
        java.io.File outputFile = new java.io.File(outputDir, fileName);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
            java.nio.channels.FileChannel channel = fos.getChannel();
            channel.write(frame.frame());
            LOGGER.info("Captured frame: {}", outputFile.getAbsolutePath());
        } catch (java.io.IOException e) {
            System.err.println("Failed to write frame: " + e.getMessage());
        }
    }
}

