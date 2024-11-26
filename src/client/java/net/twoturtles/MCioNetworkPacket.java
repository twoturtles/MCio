package net.twoturtles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;

/* Defines packet structure for Action and Observation packets */

class NetworkDefines {
    private NetworkDefines() {}
    public static final int MCIO_PROTOCOL_VERSION = 0;
    public static final int DEFAULT_ACTION_PORT = 4001;  // For receiving 4ctions
    public static final int DEFAULT_OBSERVATION_PORT = 8001;    // For sending 8bservations
}


/* Observation packets sent to agent */
record ObservationPacket(
        // Control
        int version,    // MCIO_PROTOCOL_VERSION
        String mode,    // "SYNC" or "ASYNC"
        int sequence,
        int last_action_sequence,
        int frame_sequence,

        // Observation
        ByteBuffer frame_png,
        float health,
        int cursor_mode,
        int[] cursor_pos,    // [x, y]
        float[] player_pos,   // [x, y, z]
        float player_pitch,
        float player_yaw,
        ArrayList<InventorySlot> inventory_main,
        ArrayList<InventorySlot> inventory_armor,
        ArrayList<InventorySlot> inventory_offhand
) {
    ObservationPacket {
        Validate.check(version == NetworkDefines.MCIO_PROTOCOL_VERSION, "Invalid version");
        Validate.check(cursor_pos.length == 2, "Invalid cursor_pos");
        Validate.check(cursor_mode == GLFW.GLFW_CURSOR_DISABLED ||
                cursor_mode == GLFW.GLFW_CURSOR_NORMAL, "Invalid cursorMode");
        Validate.check(player_pos.length == 3, "Invalid player_pos");
    }
}

// Like assert, but doesn't get disabled by the compiler
class Validate {
    static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

record InventorySlot(
        int slot,
        String id,
        int count
) {}

/* Serialize ObservationPacket */
class ObservationPacketPacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static byte[] pack(ObservationPacket observation) throws IOException {
        return CBOR_MAPPER.writeValueAsBytes(observation);
    }
}

/* ActionPacket sent by agent to Minecraft
 * Keep types simple to ease CBOR translation between python and java.
 * XXX Everything is native order (little-endian).
 */
record ActionPacket(
        // Control
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
        boolean reset,          // Reset observation sequence and clear all key / button presses

        // Action
        int[][] keys,           // Array of (key, action) pairs. E.g., (GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS)
        int[][] mouse_buttons,	//  Array of (button, action) pairs. E.g., (GLFW.GLFW_MOUSE_BUTTON_1, GLFW.GLFW_PRESS)
        int[][] mouse_pos
) {
    // Helper for debugging to print the double arrays nicely
    public String arrayToString(int[][] array) {
        return Arrays.deepToString(array);
    }
}

/* Deserialize ActionPacket */
class ActionPacketUnpacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());
    private static final ObjectMapper DEBUG_MAPPER = new ObjectMapper().enable(
            SerializationFeature.INDENT_OUTPUT);

    public static Optional<ActionPacket> unpack(byte[] data) {
        try {
            return Optional.of(CBOR_MAPPER.readValue(data, ActionPacket.class));
        } catch (IOException e) {
            String debugInfo = debugPacket(data);
            LOGGER.error("Failed to unpack data: {}.\nRaw packet: {}", e.getMessage(), debugInfo);
            return Optional.empty();
        }
    }

    public static String debugPacket(byte[] data) {
        try {
            JsonNode node = CBOR_MAPPER.readTree(data);
            return DEBUG_MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            // If we can't even parse as JSON tree, show hex dump
            StringBuilder hex = new StringBuilder("Unparseable CBOR bytes: ");
            for (byte b : data) {
                hex.append(String.format("%02X ", b));
            }
            return hex.toString();
        }
    }

}

