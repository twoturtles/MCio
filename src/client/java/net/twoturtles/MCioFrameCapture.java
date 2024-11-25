package net.twoturtles;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.ByteArrayOutputStream;
import java.util.List;

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
 * and stores the frame here. ObservationHandler picks up the most recent frame at the end of every tick */
public final class MCioFrameCapture {
    private static MCioFrameCapture instance;
    public final int ASYNC_CAPTURE_EVERY_N_FRAMES = 2;
    public final int BYTES_PER_PIXEL = 3;    // GL_RGB

    private final Logger LOGGER = LogUtils.getLogger();
    private final TrackPerSecond frameFPS = new TrackPerSecond("Frames");
    private final TrackPerSecond captureFPS = new TrackPerSecond("FrameCaptures");
    private boolean enabled = false;
    MCioConfig config;

    private int frameCount = 0;
    private MCioFrame lastCapturedFrame = null;

    public static MCioFrameCapture getInstance() {
        if (instance == null) {
            instance = new MCioFrameCapture();
        }
        return instance;
    }

    public void setEnabled(boolean enabled_val) { enabled = enabled_val; }
    public boolean isEnabled() { return enabled; }

    public record MCioFrame(
            int frame_count,
            int width,
            int height,
            int bytes_per_pixel,
            ByteBuffer frame
    ) { }

    private MCioFrameCapture() {
        config = MCioConfig.getInstance();
    }

    // Called by WindowMixin to hand off a new frame
    public void setLastFrame(MCioFrame frame) {
        captureFPS.count();
        lastCapturedFrame = frame;
        invokeCaptureCallbacks(frame);
    }

    public void incrementFrameCount() { frameCount++; }
    public int getFrameCount() { return frameCount; }

    public boolean shouldCaptureFrame() {
        frameFPS.count();
        if (config.mode == MCioDef.Mode.ASYNC) {
            // In async mode we're running real time. As optimization only capture every other frame.
            // Probably not necessary
            return frameCount % ASYNC_CAPTURE_EVERY_N_FRAMES == 0;
        } else {
            // In sync mode every frame is a tick, so always capture.
            return true;
        }
    }

    public MCioFrame getLastCapturedFrame() { return lastCapturedFrame; }

    /* Convert the pixels in the frame to a PNG */
    public ByteBuffer getFramePNG(MCioFrame frame) {
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

    /*
     * Provide a callback interface for captures
     */
    @FunctionalInterface
    public interface FrameCaptureCallback {
        void invokeCallback(MCioFrame frame);
    }
    private final List<FrameCaptureCallback> captureCallbacks = new ArrayList<>();
    public void registerCaptureCallback(FrameCaptureCallback callback) {
        captureCallbacks.add(callback);
    }
    private void invokeCaptureCallbacks(MCioFrame frame) {
        for (FrameCaptureCallback callback : captureCallbacks) {
            callback.invokeCallback(frame);
        }
    }

}

/* Provides hot key to save frames to files without printing a message to the screen. */
class MCioFrameSave {
    private final Logger LOGGER = LogUtils.getLogger();
    private KeyBinding captureKey;
    private int frameCount = 0;

    public void initialize() {
        // Register the keybinding (default to C (capture))
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "MCioFrameSave",
                GLFW.GLFW_KEY_C,
                MCioDef.KEY_CATEGORY
        ));

        // Register the tick event to pick up the key press.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureKey.wasPressed() && client.world != null) {
                doCapturePNG(client);
            }
        });
    }

    /* Write png to frame_captures dir */
    /* for 1280x1280 frames: PNG 1.2M, Raw 4.7M, PNG from PIL: 964K, Minecraft screenshot PNG: 1.4M */
    private void doCapturePNG(MinecraftClient client) {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getInstance().getLastCapturedFrame();
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

}

