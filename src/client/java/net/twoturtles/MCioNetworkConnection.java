package net.twoturtles;

/* Top level network interface for communicating with the agent. Spawns threads for ZMQ. */

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.util.Optional;

class MCioNetworkConnection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ZContext zContext;
    private final ZMQ.Socket actionSocket;
    private final ZMQ.Socket observationSocket;
    private final MCioConfig config = MCioConfig.getInstance();

    MCioNetworkConnection() {
        this.zContext = new ZContext();

        actionSocket = zContext.createSocket(SocketType.PULL);
        try {
            actionSocket.bind("tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST,
                    MCioConfig.getInstance().actionPort));
        } catch (ZMQException e) {
            if (e.getErrorCode() == ZMQ.Error.EADDRINUSE.getCode()) {
                LOGGER.error("MCIO Action port already in use. " +
                        "Please ensure no other instance of Minecraft/MCio is running.");
                System.exit(1);
            } else {
                throw e;
            }
        }

        observationSocket = zContext.createSocket(SocketType.PUSH);
        try {
            observationSocket.bind("tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST,
                    MCioConfig.getInstance().observationPort));
        } catch (ZMQException e) {
            if (e.getErrorCode() == ZMQ.Error.EADDRINUSE.getCode()) {
                LOGGER.error("MCIO Observation port already in use. " +
                        "Please ensure no other instance of Minecraft/MCio is running.");
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    // Receive an action from the agent
    // Returns null (Optional.empty()) when unpacking fails
    Optional<ActionPacket> recvActionPacket(boolean block) {
        try {
            int flags = block ? 0 : ZMQ.DONTWAIT;
            byte[] pkt = actionSocket.recv(flags);
            // pkt can be null if non-blocking
            return pkt != null ? ActionPacketUnpacker.unpack(pkt) : Optional.empty();
        } catch (ZMQException e) {
            // This is probably during shutdown, but maybe should return error.
            return Optional.empty();
        }
    }

    // Send an observation packet to the agent
    void sendObservationPacket(ObservationPacket observationPacket, boolean block) {
        try {
            byte[] pBytes = ObservationPacketPacker.pack(observationPacket);
            // Send to agent
            int flags = block ? 0 : ZMQ.DONTWAIT;
            boolean success = observationSocket.send(pBytes, flags);
            if (!success && observationSocket.errno() != ZMQ.Error.EAGAIN.getCode()) {
                LOGGER.warn("SEND FAILED error={}", ZMQ.Error.findByCode(observationSocket.errno()));
            }
        } catch (IOException e) {
            LOGGER.warn("ObservationPacketPacker failed");
        }
    }

    public void close() {
        if (actionSocket != null) {
            actionSocket.close();
        }
        if (observationSocket != null) {
            observationSocket.close();
        }
        if (zContext != null) {
            zContext.close();
        }
    }

}

