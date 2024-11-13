package net.twoturtles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/* Networking interface for communicating with the agent. */

class NetworkDefines {
    private NetworkDefines() {}
    public static final int MCIO_PROTOCOL_VERSION = 0;
}

/* State packets sent to agent */
record StatePacket(
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
        ByteBuffer frame_png,
        float health,
        ArrayList<InventorySlot> inventory_main,
        ArrayList<InventorySlot> inventory_armor,
        ArrayList<InventorySlot> inventory_offhand
) {}

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
        boolean key_reset,          // clear all pressed keys (useful for crashed controller).
        String message
) {}

/* Deserialize ActionPacket */
class ActionPacketUnpacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static Optional<ActionPacket> unpack(byte[] data) {
        try {
            return Optional.of(CBOR_MAPPER.readValue(data, ActionPacket.class));
        } catch (IOException e) {
            LOGGER.error("Failed to unpack data", e);
            return Optional.empty();
        }
    }
}

