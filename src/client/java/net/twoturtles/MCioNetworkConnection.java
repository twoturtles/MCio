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

    MCioNetworkConnection() {
        this.zContext = new ZContext();

        actionSocket = zContext.createSocket(SocketType.SUB);  // Sub socket for receiving actions
        actionSocket.connect("tcp://localhost:" + NetworkDefines.DEFAULT_ACTION_PORT);
        actionSocket.subscribe(new byte[0]); // Subscribe to everything

        observationSocket = zContext.createSocket(SocketType.PUB);  // Pub for sending observation
        observationSocket.bind("tcp://*:" + NetworkDefines.DEFAULT_OBSERVATION_PORT);
    }

    // Public interface to receive an action from the agent. Blocks.
    // Returns null (Optional.empty()) when unpacking fails
    Optional<ActionPacket> recvActionPacket() {
        // Block waiting for packet
        try {
            byte[] pkt = actionSocket.recv();
            return ActionPacketUnpacker.unpack(pkt);
        }  catch (ZMQException e) {
            // This is probably during shutdown, but maybe should return error.
            return Optional.empty();
        }
    }

    // Public interface to send an observation packet to the agent
    void sendObservationPacket(ObservationPacket observationPacket) {
        try {
            byte[] pBytes = ObservationPacketPacker.pack(observationPacket);
            // Send to agent
            observationSocket.send(pBytes);
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

