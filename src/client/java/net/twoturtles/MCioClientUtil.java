package net.twoturtles;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;

public class MCioClientUtil {

    // Run steps as fast as possible
    static class TestThread {
        private final Logger LOGGER = LogUtils.getLogger();

        public TestThread() {
            Thread thread = new Thread(this::threadRun, "MCio-TestThread");
            thread.setDaemon(true);
            thread.start();
        }

        private void threadRun() {
            ZContext zContext = new ZContext();
            ZMQ.Socket socket = zContext.createSocket(SocketType.PUSH);
            socket.connect("tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST, MCioConfig.getInstance().actionPort));
            ActionPacket action = new ActionPacket(
                    MCioConfig.MCIO_PROTOCOL_VERSION,
                    0,
                    new String[0],
                    false,
                    false,
                    new int[0][],
                    new int[0][],
                    new int[0][]
            );

            byte[] pBytes;
            try {
                pBytes = ActionPacketPacker.pack(action);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            while (true) {
                socket.send(pBytes);
                MCioUtil.msleep(1);
            }
        }
    }
}
