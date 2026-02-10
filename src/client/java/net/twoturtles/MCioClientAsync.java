package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public class MCioClientAsync {
  private final Logger LOGGER = LogUtils.getLogger();
  private final Minecraft client;

  private final MCioNetworkConnection connection;
  private final MCioActionHandler actionHandler;
  private final MCioObservationHandler observationHandler;

  private final AtomicBoolean running = new AtomicBoolean(true);

  // This tracks the last action sequence that has been processed before a full client tick.
  // XXX This is an attempt to determine the last action that was processed by the server. It is
  // passed back to the agent in observation packets so it can determine when it has received an
  // observation that
  // has been updated by the last action.
  // XXX This isn't taking into account when the server has updated based on the action.
  private int actionSequenceLastReceived = 0; // XXX not synchronized
  private int actionSequenceAtTickStart = 0;
  private int lastFullTickActionSequence = 0;

  // Observations are sent at the end of every client tick. Actions are received and processed on
  // a separate thread.
  public MCioClientAsync(MCioConfig config) {
    client = Minecraft.getInstance();

    connection = new MCioNetworkConnection();
    actionHandler = new MCioActionHandler(client);
    observationHandler = new MCioObservationHandler(client, config);

    connection.registerSocketStateCallback(this::socketStateCallback);

    Thread actionThread = new Thread(this::actionThreadRun, "MCio-ActionThread");
    LOGGER.info("Process-Action-Thread start");
    actionThread.start();

    ClientTickEvents.START_CLIENT_TICK.register(
        client_cb -> {
          actionSequenceAtTickStart = actionSequenceLastReceived;
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        client_cb -> {
          // Synchronization - loading lastSequenceProcessed into local
          // XXX I think this is confused
          int newActionSequence = actionSequenceLastReceived;
          if (newActionSequence >= actionSequenceAtTickStart) {
            lastFullTickActionSequence = newActionSequence;
          }
        });

    if (config.observationTrigger == MCioConfig.MCioAsyncObsTrigger.FRAME) {
      /* Send observation at the end of render tick */
      MCioFrameCapture frameCapture = MCioFrameCapture.getInstance();
      frameCapture.registerCaptureCallback(
          frame -> {
            generateObservation();
          });
    } else {
      /* Send observation at the end of every tick */
      ClientTickEvents.END_CLIENT_TICK.register(
          client_cb -> {
            generateObservation();
          });
    }
  }

  // Receive and process actions. Separate thread since it will block waiting for an action.
  private void actionThreadRun() {
    while (running.get()) {
      Optional<ActionPacket> opt = connection.recvActionPacket(true);
      if (opt.isPresent()) {
        ActionPacket action = opt.get();
        actionHandler.processAction(action);
        actionSequenceLastReceived = action.sequence();
      }
    }
  }

  void generateObservation() {
    Optional<ObservationPacket> opt =
        observationHandler.collectObservation(lastFullTickActionSequence);
    opt.ifPresent(packet -> connection.sendObservationPacket(packet, false));
  }

  /** If the Action connection goes down, automatically clear inputs. */
  private void socketStateCallback(MCioNetworkConnection.MCioSocketType type, boolean connected) {
    if (type == MCioNetworkConnection.MCioSocketType.ACTION && !connected) {
      LOGGER.info("Clearing Input (Disconnect)");
      actionHandler.requestClearInput();
    }
  }

  public void stop() {
    running.set(false);
    connection.close();
  }
}
