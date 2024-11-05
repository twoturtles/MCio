package net.twoturtles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.twoturtles.mixin.client.MouseOnCursorPosInvoker;
import net.twoturtles.util.tickTimer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/* Spawns threads for receiving commands and sending state updates. */
public class MCioController {
    private final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT_CMD = 5556;  // For receiving commands
    private static final int PORT_STATE = 5557;    // For sending screen and other state.
    private final ZContext context;
    private final ZMQ.Socket stateSocket;

    private final MinecraftClient client;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MCioController() {
        this.context = new ZContext();
        this.stateSocket = context.createSocket(SocketType.PUB);   // Pub for sending state

        this.client = MinecraftClient.getInstance();
    }

    public void start() {
        this.running.set(true);

        stateSocket.bind("tcp://*:" + PORT_STATE);

        // Start threads
        CommandHandler cmd = new CommandHandler(client, context, PORT_CMD, running);
        cmd.start();
        Thread stateThread = new Thread(this::stateThread, "MCio-StateThread");
        stateThread.start();
    }

    private void stateThread() {
        LOGGER.warn("State Thread Starting");
        LOGGER.warn("State Thread Stopping");
    }

    private void tmp() {
        final tickTimer client_timer = new tickTimer("Client");
        if (client_timer.tickCount == 100) {
            LOGGER.warn("PRESS");
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_PRESS, 0);
            });
        } else if (client_timer.tickCount == 200) {
            LOGGER.warn("RELEASE");
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_RELEASE, 0);
            });
        }

        double x = ((Math.sin((client_timer.tickCount * 2 * Math.PI) / 100) + 1) / 2) * 1000;
        double y = ((Math.cos((client_timer.tickCount * 2 * Math.PI) / 100) + 1) / 2) * 1000;
        //LOGGER.warn("x={} y={}", x, y);
        client.execute(() -> {
            ((MouseOnCursorPosInvoker) client.mouse).invokeOnCursorPos(
                    client.getWindow().getHandle(), x, y);
        });

    }

    public void stop() {
        running.set(false);
        if (stateSocket != null) {
            stateSocket.close();
        }
        if (context != null) {
            context.close();
        }
    }
}

/* Handles incoming commnands and passes to the client/render thread. Runs on own thread. */
class CommandHandler {
    private final MinecraftClient client;
    private final AtomicBoolean running;

    private final ZMQ.Socket cmdSocket;
    private final Thread cmdThread;
    private final Logger LOGGER = LogUtils.getLogger();

    public CommandHandler(MinecraftClient client, ZContext zCtx, int listen_port, AtomicBoolean running) {
        this.client = client;
        this.running = running;

        cmdSocket = zCtx.createSocket(SocketType.SUB);  // Sub socket for receiving commands
        cmdSocket.connect("tcp://localhost:" + listen_port);
        cmdSocket.subscribe(new byte[0]); // Subscribe to everything

        this.cmdThread = new Thread(this::commandThreadRun, "MCio-CommandThread");
    }

    public void start() {
        cmdThread.start();
    }

    private void commandThreadRun() {
        try {
            processCommandsLoop();
        } finally {
            cleanupSocket();
        }
    }

    private void processCommandsLoop() {
        while (running.get()) {
            try {
                processNextCommand();
            } catch (ZMQException e) {
                handleZMQException(e);
                break;
            }
        }
    }

    private void processNextCommand() {
        byte[] pkt = cmdSocket.recv();
        Optional<CmdPacket> packetOpt = CmdPacketParser.unpack(pkt);

        if (packetOpt.isEmpty()) {
            LOGGER.warn("Received invalid command packet");
            return;
        }

        CmdPacket cmd = packetOpt.get();
        LOGGER.info("CMD {}", cmd);
        for (Integer key : cmd.keys_pressed()) {
            client.execute(() -> {
                client.keyboard.onKey(client.getWindow().getHandle(),
                        key, 0, GLFW.GLFW_PRESS, 0);
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

/* Definition of CmdPackets */
record CmdPacket(
        int seq,				// sequence number
        Set<Integer> keys_pressed,
        String message
) { }

/* Serialize/deserialize CmdPackets */
class CmdPacketParser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public byte[] pack(CmdPacket cmd) throws IOException {
        return CBOR_MAPPER.writeValueAsBytes(cmd);
    }

    public static Optional<CmdPacket> unpack(byte[] data) {
        try {
            return Optional.of(CBOR_MAPPER.readValue(data, CmdPacket.class));
        } catch (IOException e) {
            LOGGER.error("Failed to unpack data", e);
            return Optional.empty();
        }
    }
}
