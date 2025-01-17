package net.twoturtles;

/* Network interface for communicating with the agent. Used by MCioClientSync/Async */

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zeromq.*;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class MCioNetworkConnection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ZContext zContext;
    // XXX
    private final AtomicBoolean connected = new AtomicBoolean(false);

    record SocketInfo(
        ZMQ.Socket socket,
        String name
    ) {}
    private final SocketInfo actionSI;
    private final SocketInfo observationSI;

    MCioNetworkConnection() {
        this.zContext = new ZContext();

        actionSI = new SocketInfo(zContext.createSocket(SocketType.PULL), "Action");
        actionSI.socket.setEventHook(e -> monitorEventCB(e, actionSI), ZMQ.EVENT_ALL);
        bindSocket(actionSI, MCioConfig.getInstance().actionPort);

        observationSI = new SocketInfo(zContext.createSocket(SocketType.PUSH), "Observation");
        observationSI.socket.setEventHook(e -> monitorEventCB(e, observationSI), ZMQ.EVENT_ALL);
        bindSocket(observationSI, MCioConfig.getInstance().observationPort);
    }

    // Receive an action from the agent
    // Returns null (Optional.empty()) when unpacking fails
    Optional<ActionPacket> recvActionPacket(boolean block) {
        try {
            int flags = block ? 0 : ZMQ.DONTWAIT;
            byte[] pkt = actionSI.socket.recv(flags);
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
            boolean success = observationSI.socket.send(pBytes, flags);
            if (!success && observationSI.socket.errno() != ZMQ.Error.EAGAIN.getCode()) {
                LOGGER.warn("SEND FAILED error={}", ZMQ.Error.findByCode(observationSI.socket.errno()));
            }
        } catch (IOException e) {
            LOGGER.warn("ObservationPacketPacker failed");
        }
    }

    void monitorEventCB(ZEvent e, SocketInfo si) {
        if (e.getEvent() == ZMonitor.Event.HANDSHAKE_PROTOCOL) {
            LOGGER.info("{} Socket Connected", si.name);
        } else if (e.getEvent() == ZMonitor.Event.DISCONNECTED) {
            LOGGER.info("{} Socket Disconnected", si.name);
        } else {
            LOGGER.debug("{} Socket Event {}", si.name, e);
        }
    }

    void bindSocket(SocketInfo si, int port) {
        try {
            si.socket.bind("tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST, port));
        } catch (ZMQException e) {
            if (e.getErrorCode() == ZMQ.Error.EADDRINUSE.getCode()) {
                LOGGER.error(
                        "MCIO {} Port {} already in use. " +
                                "Please ensure no other instance of Minecraft/MCio is using this port.",
                        si.name,
                        port
                );
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    public void close() {
        if (actionSI.socket != null) {
            actionSI.socket.close();
        }
        if (observationSI.socket != null) {
            observationSI.socket.close();
        }
        if (zContext != null) {
            zContext.close();
        }
    }

}

