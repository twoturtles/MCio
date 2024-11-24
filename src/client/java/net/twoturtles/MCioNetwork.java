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
import java.util.concurrent.atomic.AtomicBoolean;

public class MCioNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private AtomicBoolean running;
    private ZContext zContext;

    ActionConnection actionConnection;
    StateConnection stateConnection;

    MCioNetwork(AtomicBoolean running) {
        this.running = running;
        this.zContext = new ZContext();

        actionConnection = new ActionConnection(this.zContext, NetworkDefines.DEFAULT_ACTION_PORT, running);
        stateConnection = new StateConnection(this.zContext, NetworkDefines.DEFAULT_STATE_PORT, running);
    }

    public void stop() {
        running.set(false);
        if (zContext != null) {
            zContext.close();
        }
    }
}

// Keep only the most recent packet. Will drop actions/states if we get behind.
class LatestItemQueue<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private T item;

    public synchronized void put(T item) {
        if (this.item != null) {
            LOGGER.warn("Packet Drop {}", item.getClass().getSimpleName());
        }
        this.item = item;
        notifyAll();
    }

    public synchronized T get() {
        while (item == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                LOGGER.warn("Unexpected Interrupt");
            }
        }
        T result = item;
        item = null;
        return result;
    }

    // May return null
    public synchronized T getNoWait() {
        T result = item;
        item = null;
        return result;
    }
}

class ActionConnection {
    private final Logger LOGGER = LogUtils.getLogger();

    private final ZMQ.Socket actionSocket;
    private final Thread actionThread;
    private final AtomicBoolean running;

    LatestItemQueue<ActionPacket> latestAction = new LatestItemQueue<>();

    public ActionConnection(ZContext zContext, int actionPort, AtomicBoolean running) {
        this.running = running;
        actionSocket = zContext.createSocket(SocketType.SUB);  // Sub socket for receiving actions
        actionSocket.connect("tcp://localhost:" + actionPort);
        actionSocket.subscribe(new byte[0]); // Subscribe to everything

        this.actionThread = new Thread(this::actionThreadRun, "MCio-ActionThread");
        LOGGER.info("Action-Thread start");
        actionThread.start();
    }

    private void recvActionPacket() {
        // Block waiting for packet
        byte[] pkt = actionSocket.recv();
        Optional<ActionPacket> packetOpt = ActionPacketUnpacker.unpack(pkt);
        if (packetOpt.isEmpty()) {
            LOGGER.warn("Received invalid action packet");
            return;
        }

        // Place on queue for render thread to pick up.
        ActionPacket action = packetOpt.get();
        LOGGER.debug("ActionPacket {} {}", action, action.arrayToString(action.mouse_pos()));
        latestAction.put(action);
    }

    private void actionThreadRun() {
        try {
            while (running.get()) {
                try {
                    recvActionPacket();
                } catch (ZMQException e) {
                    handleZMQException(e);
                    break;
                }
            }
        } finally {
            cleanupSocket();
        }
    }

    private void handleZMQException(ZMQException e) {
        if (!running.get()) {
            LOGGER.info("Action-Thread shutting down");
        } else {
            LOGGER.error("ZMQ error in action thread", e);
        }
    }

    private void cleanupSocket() {
        LOGGER.info("Action-Thread cleanup");
        if (actionSocket != null) {
            try {
                actionSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing action socket", e);
            }
        }
    }
}

class StateConnection {
    private final Logger LOGGER = LogUtils.getLogger();
    private final ZMQ.Socket stateSocket;
    private final Thread stateThread;
    private final AtomicBoolean running;

    LatestItemQueue<StatePacket> latestState = new LatestItemQueue<>();

    public StateConnection(ZContext zContext, int statePort, AtomicBoolean running) {
        this.running = running;
        stateSocket = zContext.createSocket(SocketType.PUB);  // Pub for sending state
        stateSocket.bind("tcp://*:" + statePort);

        this.stateThread = new Thread(this::stateThreadRun, "MCio-StateThread");
        LOGGER.info("State-Thread start");
        stateThread.start();
    }

    /*
            // StateHandler sends a signal on END_CLIENT_TICK
            signalHandler.waitForSignal();
            sendNextState();
     */
    private void stateThreadRun() {
        try {
            while (running.get()) {
                sendStatePacket();
            }
        } finally {
            cleanupSocket();
        }
    }

    private void sendStatePacket() {
        // Block waiting for packet to send
        StatePacket statePacket = latestState.get();
        try {
            byte[] pBytes = StatePacketPacker.pack(statePacket);
            // Send to agent
            stateSocket.send(pBytes);
        } catch (IOException e) {
            LOGGER.warn("StatePacketPacker failed");
        }
    }

    private void cleanupSocket() {
        LOGGER.info("State-Thread cleanup");
        if (stateSocket != null) {
            try {
                stateSocket.close();
            } catch (Exception e) {
                LOGGER.error("Error closing state socket", e);
            }
        }
    }

}
