package net.twoturtles;

/* Top level network interface for communicating with the agent. Spawns threads for ZMQ. */

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.util.Optional;

class MCioNetworkConnection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ZContext zContext;
    private final ZMQ.Socket actionSocket;
    private final ZMQ.Socket stateSocket;

    MCioNetworkConnection() {
        this.zContext = new ZContext();

        actionSocket = zContext.createSocket(SocketType.SUB);  // Sub socket for receiving actions
        actionSocket.connect("tcp://localhost:" + NetworkDefines.DEFAULT_ACTION_PORT);
        actionSocket.subscribe(new byte[0]); // Subscribe to everything

        stateSocket = zContext.createSocket(SocketType.PUB);  // Pub for sending state
        stateSocket.bind("tcp://*:" + NetworkDefines.DEFAULT_STATE_PORT);
    }

    // Public interface to receive an action from the agent. Blocks.
    // Returns null (Optional.empty()) when unpacking fails
    Optional<ActionPacket> recvActionPacket() {
        // Block waiting for packet
        byte[] pkt = actionSocket.recv();
        return ActionPacketUnpacker.unpack(pkt);
    }

    // Public interface to send a state packet to the agent
    void sendStatePacket(StatePacket statePacket) {
        try {
            byte[] pBytes = StatePacketPacker.pack(statePacket);
            // Send to agent
            stateSocket.send(pBytes);
        } catch (IOException e) {
            LOGGER.warn("StatePacketPacker failed");
        }
    }

    public void close() {
        if (actionSocket != null) {
            actionSocket.close();
        }
        if (stateSocket != null) {
            stateSocket.close();
        }
        if (zContext != null) {
            zContext.close();
        }
    }

}

