package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Run steps as fast as possible. Uses threads to send / recv actions and observations to the MCio
 * ports. MCIO_HIDE_WINDOW=true MCIO_SYNC_SPEED_TEST=true MCIO_MODE=sync - ObservationsSent
 * per-second=320.5 MCIO_SYNC_SPEED_TEST=true MCIO_MODE=sync - ObservationsSent per-second=305.7
 */
public class SpeedTest {
  private final Logger LOGGER = LogUtils.getLogger();
  ZContext zContext = new ZContext();

  public SpeedTest() {
    Thread actionThread = new Thread(this::actionThreadRun, "MCio-ActionTestThread");
    actionThread.setDaemon(true);
    actionThread.start();

    Thread observationThread = new Thread(this::observationThreadRun, "MCio-ObservationTestThread");
    observationThread.setDaemon(true);
    observationThread.start();
  }

  private void actionThreadRun() {
    ZMQ.Socket socket = zContext.createSocket(SocketType.PUSH);
    socket.connect(
        "tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST, MCioConfig.getInstance().actionPort));
    TrackPerSecond tps = new TrackPerSecond("TestSend");

    ActionPacket action =
        new ActionPacket(
            MCioConfig.MCIO_PROTOCOL_VERSION,
            0,
            new String[0],
            false,
            false,
            new InputEvent[] {},
            new double[0][],
            new ArrayList<>());

    byte[] pBytes;
    try {
      pBytes = ActionPacketPacker.pack(action);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    while (true) {
      tps.count();
      // This will block when the action recv queue fills up.
      socket.send(pBytes);
      // MCioUtil.msleep(1);
    }
  }

  private void observationThreadRun() {
    ZMQ.Socket socket = zContext.createSocket(SocketType.PULL);
    socket.connect(
        "tcp://%s:%d".formatted(MCioConfig.DEFAULT_HOST, MCioConfig.getInstance().observationPort));
    TrackPerSecond tps = new TrackPerSecond("TestRecv");

    while (true) {
      tps.count();
      socket.recv();
    }
  }
}
