package net.twoturtles;

import java.util.Optional;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.twoturtles.util.tickTimer;
import net.twoturtles.mixin.client.MouseOnCursorPosInvoker;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

class MCIO_CONST {
	public static final String KEY_CATEGORY = "MCio";
}

record CmdPacket(
		int seq,				// sequence number
		Set<Integer> keys_pressed,
		String message
) { }

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

public class MCioClient implements ClientModInitializer {
	/* screen capture */
	private final Logger LOGGER = LogUtils.getLogger();
	private final MCioFrameCapture fcap = new MCioFrameCapture();
	private final MCioKeys keys = new MCioKeys();
	private MinecraftController mc_ctrl;

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		LOGGER.info("Client Init");
		tickTimer.do_log = false;

		mc_ctrl = new MinecraftController();
		mc_ctrl.start();
		fcap.initialize();
		keys.initialize();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("Client Shutdown");
			if (mc_ctrl != null) {
				mc_ctrl.stop();
			}
		});
	}
}

/* XXX Auto-set pauseOnLostFocus:false */

class MinecraftController {
	private final Logger LOGGER = LogUtils.getLogger();
	private static final int PORT_CMD = 5556;  // For receiving commands
	private static final int PORT_STATE = 5557;    // For sending screen and other state.
	private final ZContext context;
	private final ZMQ.Socket cmdSocket;
	private final ZMQ.Socket stateSocket;

	private final MinecraftClient client;
	private volatile boolean running;

	public MinecraftController() {
		this.context = new ZContext();
		this.cmdSocket = context.createSocket(SocketType.SUB);  // Sub socket for receiving commands
		this.stateSocket = context.createSocket(SocketType.PUB);   // Pub for sending state

		this.client = MinecraftClient.getInstance();
		this.running = true;
	}

	public void start() {
		// Bind sockets
		cmdSocket.bind("tcp://*:" + PORT_CMD);
		cmdSocket.subscribe(new byte[0]); // Subscribe to everything

		stateSocket.bind("tcp://*:" + PORT_STATE);

		// Start threads
		Thread cmdThread = new Thread(this::commandThread, "MCio-CommandThread");
		cmdThread.start();
		Thread stateThread = new Thread(this::stateThread, "MCio-StateThread");
		stateThread.start();
	}



	public void commandThread() {
		try {
			processCommandsLoop();
		} finally {
			cleanupSocket();
		}
	}

	private void processCommandsLoop() {
		while (running) {
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
	}

	private void handleZMQException(ZMQException e) {
		if (!running) {
			LOGGER.info("Command thread shutting down");
		} else {
			LOGGER.error("ZMQ error in command thread", e);
		}
	}

	private void cleanupSocket() {
		if (cmdSocket != null) {
			try {
				cmdSocket.close();
			} catch (Exception e) {
				LOGGER.error("Error closing command socket", e);
			}
		}
	}




	private void commandThread() {
		LOGGER.warn("Command Thread Starting");
		while (running) {
			// Receive command
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
		LOGGER.warn("Command Thread Stopping");
	}

	private zmqInnerLoop () {
		try {
			// Receive command
			byte[] pkt = cmdSocket.recv();
			Optional<CmdPacket> packetOpt = CmdPacketParser.unpack(pkt);
			if (packetOpt.isEmpty()) {
				LOGGER.warn("Received invalid command packet");
				return;
			}
			CmdPacket cmd = packetOpt.get();
			LOGGER.info("CMD {}", cmd);
		} catch (ZMQException e) {
			if (!running) {
				// Normal termination during shutdown
				LOGGER.info("Command thread shutting down");
				break;
			} else {
				// Unexpected error during normal operation
				LOGGER.error("ZMQ error in command thread", e);
				break;
			}
		}
	}
	private void zmqLoop() {
			try {
				while (running) {
				}
			} finally {
				// Clean up
				if (cmdSocket != null) {
					try {
						cmdSocket.close();
					} catch (Exception e) {
						LOGGER.error("Error closing command socket", e);
					}
				}
			}
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
		running = false;
		if (cmdSocket != null) {
			cmdSocket.close();
		}
		if (stateSocket != null) {
			stateSocket.close();
		}
		if (context != null) {
			context.close();
		}
	}

}

class MCioFrameCapture {
	private final Logger LOGGER = LogUtils.getLogger();
	private KeyBinding captureKey;
	private int frameCount = 0;

	public void initialize() {
		LOGGER.info("Init");

		// Register the keybinding (default to F8)
		captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioFrameCapture",
				GLFW.GLFW_KEY_F8,
				MCIO_CONST.KEY_CATEGORY
		));

		// Register the tick event
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (captureKey.wasPressed() && client.world != null) {
				doCapture(client);
			}
		});
	}

	private void doCapture(MinecraftClient client) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		String fileName = String.format("frame_%s_%d.png", timestamp, frameCount++);
		LOGGER.info("Captured frame: {}", fileName);

		// Capture the frame using Minecraft's screenshot recorder
		ScreenshotRecorder.saveScreenshot(
				client.runDirectory,
				fileName,
				client.getFramebuffer(),
				(message) -> {}       // No message to the UI
		);
	}
}

class MCioKeys {
	private final Logger LOGGER = LogUtils.getLogger();
	private KeyBinding breakKey, nextKey;
	private final tickTimer client_timer = new tickTimer("Client");

	public void initialize() {
		LOGGER.info("Init");

		breakKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioIO_break",
				GLFW.GLFW_KEY_B,
				MCIO_CONST.KEY_CATEGORY
		));
		nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"MCioIO_next",
				GLFW.GLFW_KEY_N,
				MCIO_CONST.KEY_CATEGORY
		));
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			client_timer.start();

			if (breakKey.wasPressed() && client.world != null) {
				LOGGER.info("Break-Toggle");
				/* Tell server */
				MCio.isFrozen.set(!MCio.isFrozen.get());
			}
			if (nextKey.wasPressed() && client.world != null) {
				LOGGER.info("Next");
				client.player.networkHandler.sendCommand("tick step");
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(server -> {
			client_timer.end();
		});
	}
}

