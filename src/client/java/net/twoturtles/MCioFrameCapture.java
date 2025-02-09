package net.twoturtles;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.*;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallback;

/* Interface and state storage for WindowMixin:beforeSwap. beforeSwap does the actual capture
 * and stores the frame here. ObservationHandler picks up the most recent frame at the end of every tick */
public final class MCioFrameCapture {
    public final int ASYNC_CAPTURE_EVERY_N_FRAMES = 1;
    public final int BYTES_PER_PIXEL = 3;  // GL_RGB

    private final Logger LOGGER = LogUtils.getLogger();
    private final TrackPerSecond frameFPS = new TrackPerSecond("Frames");
    private final TrackPerSecond captureFPS = new TrackPerSecond("FrameCaptures");
    private final MCioConfig config = MCioConfig.getInstance();
    private boolean enabled = false;

    private int frameSequence = 0;  // Total number of frames so far
    private int frameCaptureSequence = 0;  // Number of frames
    private MCioFrame lastCapturedFrame = null;

    private int width = 1280;
    private int height = 720;
    private GpuBuffer pixelBuffer = null;
    private GlFenceSync fenceSync = null;

    // Singleton instance
    private static final MCioFrameCapture INSTANCE = new MCioFrameCapture();
    public static MCioFrameCapture getInstance() {
        return INSTANCE;
    }

    public void setEnabled(boolean enabled_val) { enabled = enabled_val; }
    public boolean isEnabled() { return enabled; }

    public record MCioFrame(
            int frame_sequence,
            int frame_capture_sequence,
            int width,
            int height,
            int bytes_per_pixel,
            ByteBuffer frame
    ) { }

    private GpuBuffer getPixelBuffer() {
        // Call of opengl too early (eg: initialization of fabric mod) will cause error.
        if (this.pixelBuffer == null) {
            this.pixelBuffer = new GpuBuffer(GlBufferTarget.PIXEL_PACK, GlUsage.STREAM_READ, 0);
            this.pixelBuffer.resize(this.width * this.height * this.BYTES_PER_PIXEL);
        }
        return this.pixelBuffer;
    }

    // Called by WindowMixin to hand off a new frame
    public void capture(Framebuffer framebuffer) {
        if (this.fenceSync == null) {
            if (framebuffer.textureWidth != this.width || framebuffer.textureHeight != this.height) {
                this.width = framebuffer.textureWidth;
                this.height = framebuffer.textureHeight;
                this.getPixelBuffer().resize(this.width * this.height * this.BYTES_PER_PIXEL);
            }

            frameCaptureSequence++;
            captureFPS.count();

            this.getPixelBuffer().bind();
            GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, framebuffer.fbo);
            GlStateManager._readPixels(0, 0, this.width, this.height,
                    GlConst.GL_RGB, GlConst.GL_UNSIGNED_BYTE, 0L);
            GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, 0);
            this.fenceSync = new GlFenceSync();
        }
    }

    public void upload() {
        // Read and send the captured frame within an observation packet.
        if (this.fenceSync != null) {
            if (this.fenceSync.wait(0L)) {
                this.fenceSync = null;

                try (GpuBuffer.ReadResult readResult = this.getPixelBuffer().read()) {
                    if (readResult != null) {
                        MCioFrame frame = new MCioFrame(frameSequence, frameCaptureSequence, this.width, this.height,
                                this.BYTES_PER_PIXEL, readResult.getBuf());
                        lastCapturedFrame = frame;
                        invokeCaptureCallbacks(frame);
                    }
                }
            }
        }
    }

    public void captureDebug(String name, ByteBuffer pixelBuffer, int width, int height) {
        pixelBuffer.rewind();
        MCioFrame frame = new MCioFrame(frameSequence, frameCaptureSequence,
                width, height, BYTES_PER_PIXEL, pixelBuffer);
        String fileName = String.format("%03d-%s.png", frame.frame_sequence(), name);
        MCioFrameSave.getInstance().saveFrame(frame, fileName);
    }

    public void incrementFrameSequence() { frameSequence++; }
    public int getFrameSequence() { return frameSequence; }

    public boolean shouldCaptureFrame() {
        frameFPS.count();
        if (config.mode == MCioConfig.MCioMode.ASYNC) {
            // In async mode we're running real time. As optimization only capture every other frame.
            // Probably not necessary
            return frameSequence % ASYNC_CAPTURE_EVERY_N_FRAMES == 0;
        } else {
            // In sync mode every frame is a tick, so always capture.
            return true;
        }
    }

    public MCioFrame getLastCapturedFrame() { return lastCapturedFrame; }


    /*
     * Convert the pixels in the frame to a PNG / JPEG
     */

    @FunctionalInterface
    private interface FrameWriter {
        boolean convert_write(STBIWriteCallback writeToStreamCb);
    }

    public ByteBuffer getFramePNG(MCioFrame frame) {
        return writeFrame(frame, writeToStreamCb ->
                STBImageWrite.stbi_write_png_to_func(
                        writeToStreamCb,
                        0L,
                        frame.width(),
                        frame.height(),
                        BYTES_PER_PIXEL,
                        frame.frame(),
                        frame.width() * BYTES_PER_PIXEL
                )
        );
    }

    public ByteBuffer getFrameJPEG(MCioFrame frame) {
        return getFrameJPEG(frame, MCioConfig.DEFAULT_FRAME_QUALITY);
    }
    public ByteBuffer getFrameJPEG(MCioFrame frame, int quality) {
        return writeFrame(frame, writeToStreamCb ->
                STBImageWrite.stbi_write_jpg_to_func(
                        writeToStreamCb,
                        0L,
                        frame.width(),
                        frame.height(),
                        BYTES_PER_PIXEL,
                        frame.frame(),
                        quality
                ) != 0  // Convert return to boolean. stbi_write_png_to_func() already does this
        );
    }
    public ByteBuffer getFrameRAW(MCioFrame frame) {
//      return flipFrame(frame);
        return frame.frame;
    }

    /* Vertical flip for OpenGL frames
     * XXX This is expensive. It ends up using >7% of the CPU.
     * Going to export upside-down frames until something faster is found.
     */
    ByteBuffer flipFrame(MCioFrame frame) {
        int stride = frame.width * frame.bytes_per_pixel; // Number of bytes per row
        ByteBuffer flippedBuffer = ByteBuffer.allocateDirect(frame.frame.capacity());

        for (int row = 0; row < frame.height; row++) {
            int srcPos = row * stride;
            int destPos = (frame.height - 1 - row) * stride;
            for (int i = 0; i < stride; i++) {
                flippedBuffer.put(destPos + i, frame.frame.get(srcPos + i));
            }
        }

        return flippedBuffer;
    }

    public ByteBuffer getFrameRaw(MCioFrame frame) {
        return frame.frame();
    }

    private ByteBuffer writeFrame(MCioFrame frame, FrameWriter frameWriter) {
        frame.frame().rewind(); // Ensure the buffer is at the start

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        STBIWriteCallback writeToStreamCb = STBIWriteCallback.create((context, data, size) -> {
            byte[] bytes = new byte[size];
            MemoryUtil.memByteBuffer(data, size).get(bytes);
            outputStream.write(bytes, 0, size);
        });

        /* Flip the OpenGL frame */
        STBImageWrite.stbi_flip_vertically_on_write(true);

        boolean success = frameWriter.convert_write(writeToStreamCb);
        if (!success) {
            throw new RuntimeException("Failed to write frame");
        }

        return ByteBuffer.wrap(outputStream.toByteArray());
    }

    /**
     * Provide a callback interface for captures
     * Note: These are called during render processing just before
     * net.minecraft.client.util.Window.swapBuffers() is called. Be careful
     * about large amounts of processing or blocking.
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
    private static MCioFrameSave instance;
    private final Logger LOGGER = LogUtils.getLogger();
    private KeyBinding captureKey;

    public static void initialize() {
        // Use for initial setup.
        getInstance();
    }

    public static MCioFrameSave getInstance() {
        if (instance == null) {
            instance = new MCioFrameSave();
        }
        return instance;
    }

    private MCioFrameSave() {
        // Register the keybinding (default to V)
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "MCioFrameSave",
                GLFW.GLFW_KEY_V,
                MCioConfig.KEY_CATEGORY
        ));

        // Register the tick event to pick up the key press.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureKey.wasPressed() && client.world != null) {
                doCapture();
            }
        });
    }

    // Save a frame to disk. It goes in the frame_captures dir.
    public void saveFrame(MCioFrameCapture.MCioFrame frame) {
        String fileName = String.format("frame_%03d.png", frame.frame_sequence());
        saveFrame(frame, fileName);
    }

    // Allow fileName override
    public void saveFrame(MCioFrameCapture.MCioFrame frame, String fileName) {
        frame.frame().rewind();  // Make sure we're at the start of the buffer

        java.io.File outputDir = new java.io.File("frame_captures");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        java.io.File outputFile = new java.io.File(outputDir, fileName);

        // Flip - openGL frames are upside down
        STBImageWrite.stbi_flip_vertically_on_write(true);
        STBImageWrite.stbi_write_png(outputFile.getAbsolutePath(), frame.width(), frame.height(),
                3, frame.frame(), frame.width() * 3);
        LOGGER.info("Captured frame: {}", outputFile.getAbsolutePath());
    }

    /* Callback for captureKey. Write png to frame_captures dir */
    /* for 1280x1280 frames: PNG 1.2M, Raw 4.7M, PNG from PIL: 964K, Minecraft screenshot PNG: 1.4M */
    private void doCapture() {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getInstance().getLastCapturedFrame();
        saveFrame(frame);
    }

}
