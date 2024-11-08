package net.twoturtles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import net.twoturtles.mixin.client.MouseMixin;
import net.twoturtles.util.TrackFPS;

/* Definition of CmdPacket
 * Keep types simple to ease CBOR translation between python and java.
 * XXX Everything is native order (little-endian).
 */
record CmdPacket(
        int seq,				// sequence number
        Set<Integer> keys_pressed,
        Set<Integer> keys_released,
        Set<Integer> mouse_buttons_pressed,
        Set<Integer> mouse_buttons_released,
        boolean mouse_pos_update,
        int mouse_pos_x,
        int mouse_pos_y,
        boolean key_reset,          // clear all pressed keys (useful for crashed controller).
        String message
) { }

/* Deserialize CmdPacket */
class CmdPacketUnpacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static Optional<CmdPacket> unpack(byte[] data) {
        try {
            return Optional.of(CBOR_MAPPER.readValue(data, CmdPacket.class));
        } catch (IOException e) {
            LOGGER.error("Failed to unpack data", e);
            return Optional.empty();
        }
    }
}

record StatePacket(
        int seq,				// sequence number
        int width,
        int height,
        int bytes_per_pixel,
        ByteBuffer frame,
        String message
) { }

/* Serialize StatePacket */
class StatePacketPacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static byte[] pack(StatePacket state) throws IOException {
        return CBOR_MAPPER.writeValueAsBytes(state);
    }
}

/* Top-level class. Runs on client thread.
 * Spawns threads for receiving commands and sending state updates. */
public class MCioController {
    private final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT_CMD = 5556;  // For receiving commands
    private static final int PORT_STATE = 5557;    // For sending screen and other state.
    private final ZContext context;

    private final MinecraftClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MCioController() {
        this.context = new ZContext();

        this.client = MinecraftClient.getInstance();
    }

    public void start() {
        this.running.set(true);

        // Start threads
        CommandHandler cmd = new CommandHandler(client, context, PORT_CMD, running);
        cmd.start();

        StateHandler state = new StateHandler(client, context, PORT_STATE, running);
        state.start();
    }

    public void stop() {
        running.set(false);
        if (context != null) {
            context.close();
        }
    }
}

class StateHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;
    private final SignalWithLatch signalHandler = new SignalWithLatch();

    private final ZMQ.Socket stateSocket;
    private final Thread stateThread;
    private final Logger LOGGER = LogUtils.getLogger();
    private static final TrackFPS sendFPS = new TrackFPS("SEND");

    public StateHandler(MinecraftClient client, ZContext zCtx, int listen_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        MCioFrameCapture.setEnabled(true);

        stateSocket = zCtx.createSocket(SocketType.PUB);  // Pub for sending state
        stateSocket.bind("tcp://*:" + listen_port);

        this.stateThread = new Thread(this::stateThreadRun, "MCio-StateThread");

        /* Send state at the end of every tick */
        ClientTickEvents.END_CLIENT_TICK.register(client_cb -> {
            /* This will run on the client thread. */
            signalHandler.sendSignal();
        });
    }

    public void start() {
        LOGGER.warn("Thread start");
        stateThread.start();
    }

    class SignalWithLatch {
        private CountDownLatch latch = new CountDownLatch(1);

        public void waitForSignal() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted");
            }
            latch = new CountDownLatch(1);  // Reset for next use
        }

        public void sendSignal() {
            latch.countDown();
        }
    }

    private void stateThreadRun() {
        try {
            while (running.get()) {
                signalHandler.waitForSignal();;
                sendNextState();
            }
        } finally {
            cleanupSocket();
        }
    }

    private void sendNextState() {
        MCioFrameCapture.MCioFrame frame = MCioFrameCapture.getLastCapturedFrame();
        if (frame != null && frame.frame() != null) {
            /* If FPS SEND > FPS CAPTURE, we'll be sending duplicate frames. */
            if (sendFPS.count()) {
                LOGGER.warn("SEND FRAME {}", frame.frame_count());
            }
            //test(pixelBuffer);
            StatePacket statePkt = new StatePacket(frame.frame_count(), frame.width(), frame.height(),
                    frame.bytes_per_pixel(), frame.frame(), "hello...");
            try {
                byte[] pbytes = StatePacketPacker.pack(statePkt);
                stateSocket.send(pbytes);
            } catch (IOException e) {
                LOGGER.warn("StatePacketPacker failed");
            }
        }
    }

    private void test(ByteBuffer pixelBuffer) {
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

    private void handleZMQException(ZMQException e) {
        if (!running.get()) {
            LOGGER.info("State thread shutting down");
        } else {
            LOGGER.error("ZMQ error in state thread", e);
        }
    }

    private void cleanupSocket() {
        LOGGER.info("State thread cleanup");
        if (stateSocket != null) {
            try {
                stateSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing state socket", e);
            }
        }
    }
}


/* Handles incoming commands and passes to the client/render thread. Runs on own thread. */
class CommandHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;

    private final ZMQ.Socket cmdSocket;
    private final Thread cmdThread;
    private final Logger LOGGER = LogUtils.getLogger();

    /* XXX Clear all commands if remote controller disconnects? */
    public CommandHandler(MinecraftClient client, ZContext zCtx, int remote_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        cmdSocket = zCtx.createSocket(SocketType.SUB);  // Sub socket for receiving commands
        cmdSocket.connect("tcp://localhost:" + remote_port);
        cmdSocket.subscribe(new byte[0]); // Subscribe to everything

        this.cmdThread = new Thread(this::commandThreadRun, "MCio-CommandThread");
    }

    public void start() {
        cmdThread.start();
    }

    private void commandThreadRun() {
        try {
            while (running.get()) {
                try {
                    processNextCommand();
                } catch (ZMQException e) {
                    handleZMQException(e);
                    break;
                }
            }
        } finally {
            cleanupSocket();
        }
    }

    private void processNextCommand() {
        // Block waiting for next command.
        byte[] pkt = cmdSocket.recv();
        Optional<CmdPacket> packetOpt = CmdPacketUnpacker.unpack(pkt);
        if (packetOpt.isEmpty()) {
            LOGGER.warn("Received invalid command packet");
            return;
        }

        CmdPacket cmd = packetOpt.get();
        LOGGER.info("CMD {}", cmd);

        /* Keyboard handlers */
        for (int key : cmd.keys_pressed()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int key : cmd.keys_released()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_RELEASE, 0);
            });
        }

        /* Mouse handlers */
        if (cmd.mouse_pos_update()) {
            client.execute(() -> {
                ((MouseMixin.OnCursorPosInvoker) client.mouse).invokeOnCursorPos(
                        client.getWindow().getHandle(), cmd.mouse_pos_x(), cmd.mouse_pos_y());
            });
        }
        for (int button : cmd.mouse_buttons_pressed()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_PRESS, 0);
            });
        }
        for (int button : cmd.mouse_buttons_released()) {
            client.execute(() -> {
                ((MouseMixin.OnMouseButtonInvoker) client.mouse).invokeOnMouseButton(
                        client.getWindow().getHandle(), button, GLFW.GLFW_RELEASE, 0);
            });
        }
    }

    private void handleZMQException(ZMQException e) {
        if (!running.get()) {
            LOGGER.info("Command thread shutting down");
        } else {
            LOGGER.error("ZMQ error in command thread", e);
        }
    }

    private void cleanupSocket() {
        LOGGER.info("Command thread cleanup");
        if (cmdSocket != null) {
            try {
                cmdSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing command socket", e);
            }
        }
    }
}

