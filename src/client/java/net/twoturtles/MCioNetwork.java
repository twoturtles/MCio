package net.twoturtles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import com.mojang.logging.LogUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;

/* Networking interface for communicating with the agent. */

class NetworkDefines {
    private NetworkDefines() {}
    public static final int MCIO_PROTOCOL_VERSION = 0;
}

class Validate {
    static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}


/* State packets sent to agent */
record StatePacket(
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
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
    StatePacket {
        Validate.check(version == NetworkDefines.MCIO_PROTOCOL_VERSION, "Invalid version");
        Validate.check(cursor_pos.length == 2, "Invalid cursor_pos");
        Validate.check(cursor_mode == GLFW.GLFW_CURSOR_DISABLED ||
                cursor_mode == GLFW.GLFW_CURSOR_NORMAL, "Invalid cursorMode");
        Validate.check(player_pos.length == 3, "Invalid player_pos");
    }
}

record InventorySlot(
        int slot,
        String id,
        int count
) {}

/* Serialize StatePacket */
class StatePacketPacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static byte[] pack(StatePacket state) throws IOException {
        return CBOR_MAPPER.writeValueAsBytes(state);
    }
}

/* ActionPacket sent by agent to Minecraft
 * Keep types simple to ease CBOR translation between python and java.
 * XXX Everything is native order (little-endian).
 */
record ActionPacket(
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
        Set<Integer> keys_pressed,
        Set<Integer> keys_released,
        Set<Integer> mouse_buttons_pressed,
        Set<Integer> mouse_buttons_released,
        boolean mouse_pos_update,
        int mouse_pos_x,
        int mouse_pos_y,
        boolean key_reset          // TODO clear all pressed keys (useful for crashed controller).
) {}

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

