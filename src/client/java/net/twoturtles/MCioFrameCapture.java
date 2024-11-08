package net.twoturtles;

import java.nio.ByteBuffer;
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
