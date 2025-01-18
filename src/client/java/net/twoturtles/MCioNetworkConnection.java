package net.twoturtles;

/* Network interface for communicating with the agent. Used by MCioClientSync/Async */

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zeromq.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class MCioNetworkConnection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ZContext zContext;
    public enum MCioSocketType {
        ACTION, OBSERVATION
    }

    class SocketManager {
        MCioSocketType type;
        ZMQ.Socket socket;
        AtomicBoolean connected;

        SocketManager(MCioSocketType mcioType, SocketType zmqType) {
            this.type = mcioType;
            this.socket = zContext.createSocket(zmqType);
            this.connected = new AtomicBoolean(false);
        }
    }
    private final SocketManager actionSM;
    private final SocketManager observationSM;

    MCioNetworkConnection() {
        this.zContext = new ZContext();

        actionSM = new SocketManager(MCioSocketType.ACTION, SocketType.PULL);
        actionSM.socket.setEventHook(e -> monitorEventCB(e, actionSM), ZMQ.EVENT_ALL);
        bindSocket(actionSM, MCioConfig.getInstance().actionPort);

        observationSM = new SocketManager(MCioSocketType.OBSERVATION, SocketType.PUSH);
        observationSM.socket.setEventHook(e -> monitorEventCB(e, observationSM), ZMQ.EVENT_ALL);
        bindSocket(observationSM, MCioConfig.getInstance().observationPort);
    }

    // Receive an action from the agent
    // Returns null (Optional.empty()) when unpacking fails
    Optional<ActionPacket> recvActionPacket(boolean block) {
        try {
            int flags = block ? 0 : ZMQ.DONTWAIT;
            byte[] pkt = actionSM.socket.recv(flags);
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
            boolean success = observationSM.socket.send(pBytes, flags);
            if (!success && observationSM.socket.errno() != ZMQ.Error.EAGAIN.getCode()) {
                LOGGER.warn("SEND FAILED error={}", ZMQ.Error.findByCode(observationSM.socket.errno()));
            }
        } catch (IOException e) {
            LOGGER.warn("ObservationPacketPacker failed");
        }
    }

    void monitorEventCB(ZEvent e, SocketManager mgr) {
        if (e.getEvent() == ZMonitor.Event.HANDSHAKE_PROTOCOL) {
            LOGGER.info("{} Socket Connected", mgr.type);
            mgr.connected.set(true);
            invokeSocketStateCallbacks(mgr.type, true);
        } else if (e.getEvent() == ZMonitor.Event.DISCONNECTED) {
            LOGGER.info("{} Socket Disconnected", mgr.type);
            mgr.connected.set(false);
            invokeSocketStateCallbacks(mgr.type, false);
        } else {
            LOGGER.debug("{} Socket Event {}", mgr.type, e);
        }
    }

    /**
     * Provide a callback interface for socket status
     * The callbacks will run on a zmq io thread.
     */
    @FunctionalInterface
    public interface SocketStateCallback {
        void invokeCallback(MCioSocketType type, boolean connected);
    }
    private final List<SocketStateCallback> stateCallbacks = new ArrayList<>();
    public void registerSocketStateCallback(SocketStateCallback callback) {
        stateCallbacks.add(callback);
    }
    private void invokeSocketStateCallbacks(MCioSocketType type, boolean connected) {
        for (SocketStateCallback callback : stateCallbacks) {
            callback.invokeCallback(type, connected);
        }
    }


    void bindSocket(SocketManager mgr, int port) {
        try {
            mgr.socket.bind("tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST, port));
        } catch (ZMQException e) {
            if (e.getErrorCode() == ZMQ.Error.EADDRINUSE.getCode()) {
                LOGGER.error(
                        "MCIO {} Port {} already in use. " +
                                "Please ensure no other instance of Minecraft/MCio is using this port.",
                        mgr.type,
                        port
                );
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    public void close() {
        if (actionSM.socket != null) {
            actionSM.socket.close();
        }
        if (observationSM.socket != null) {
            observationSM.socket.close();
        }
        if (zContext != null) {
            zContext.close();
        }
    }

}

