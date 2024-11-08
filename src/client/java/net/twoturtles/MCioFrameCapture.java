package net.twoturtles;

import java.nio.ByteBuffer;

/* Interface and state storage for WindowMixin:beforeSwap */
public final class MCioFrameCapture {
    private static boolean enabled = false;
    private static int frameCount = 0;
    private static MCioFrame lastFrame = null;
    public static final int CAPTURE_EVERY_N_FRAMES = 10;
    public static final int BYTES_PER_PIXEL = 3;    // GL_RGB

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

    public static void setLastFrame(MCioFrame frame) {lastFrame = frame;}
    public static MCioFrame getLastFrame() {
        MCioFrame ret = lastFrame;
        lastFrame = null;
        return ret;
    }

    public static void setEnabled(boolean enabled_val) { enabled = enabled_val; }
    public static boolean isEnabled() { return enabled; }

    public static void incrementFrameCount() { frameCount++; }
    public static int getFrameCount() { return frameCount; }

    public static boolean shouldCaptureFrame() {
        return frameCount % CAPTURE_EVERY_N_FRAMES == 0;
    }
}

