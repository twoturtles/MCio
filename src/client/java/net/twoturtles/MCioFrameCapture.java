package net.twoturtles;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;

public final class MCioFrameCapture {
    private static boolean enabled = false;
    private static ByteBuffer pixelBuffer = null;
    private static int frameCount = 0;
    public static final int CAPTURE_EVERY_N_FRAMES = 10;

    private MCioFrameCapture() {
        throw new AssertionError("Singleton: do not instantiate");
    }

    public static void setEnabled(boolean enabled_val) {
        enabled = enabled_val;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static ByteBuffer getBuffer(int width, int height) {
        if (pixelBuffer == null || pixelBuffer.capacity() != width * height * 4) {
            pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
        }
        return pixelBuffer;
    }

    public static void incrementFrameCount() { frameCount++; }
    public static int getFrameCount() { return frameCount; }

    public static boolean shouldCaptureFrame() {
        return frameCount % CAPTURE_EVERY_N_FRAMES == 0;
    }
}
