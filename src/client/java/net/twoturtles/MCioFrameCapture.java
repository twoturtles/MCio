package net.twoturtles;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;

public final class MCioFrameCapture {
    private static boolean enabled = false;
    private static int frameCount = 0;
    private static ByteBuffer lastBuffer = null;
    public static final int CAPTURE_EVERY_N_FRAMES = 10;
    public static final int BYTES_PER_PIXEL = 3;    // GL_RGB

    private MCioFrameCapture() {
        throw new AssertionError("Singleton: do not instantiate");
    }

    public static void setLastBuffer(ByteBuffer buf) {lastBuffer = buf;}
    public static ByteBuffer getLastBuffer() {
        ByteBuffer ret = lastBuffer;
        lastBuffer = null;
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
