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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

/* Defines packet structure for Action and Observation packets */

/*
 * Note: Each MCIO_TYPE class must have the same name as its corresponding @MCioType class in
 * mcio_ctrl for proper type matching
 * The JsonTypeInfo annotation adds a property to the object as it's encoded (into CBOR).
 * The property name is MCIO_TYPE ("__mcio_type__") and the value will be the MINIMAL_CLASS name,
 * which is the class name preceded by a dot (e.g. ".ObservationPacket").
 */

/* Observation packets sent to agent */
@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record ObservationPacket(
        // Control
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
        String mode,    // "SYNC" or "ASYNC"
        int last_action_sequence,
        int frame_sequence,
        int frame_height,
        int frame_width,
        String frame_type,  // See MCioConfig.MCioFrameType

        // Observation
        ByteBuffer frame,
        int cursor_mode,
        double[] cursor_pos,    // [x, y]
        float health,
        float[] player_pos,   // [x, y, z]
        float player_pitch,
        float player_yaw,
        ArrayList<InventorySlot> inventory_main,
        ArrayList<InventorySlot> inventory_armor,
        ArrayList<InventorySlot> inventory_offhand,

        ArrayList<Option> options
) {
    ObservationPacket {
        Validate.check(version == MCioConfig.MCIO_PROTOCOL_VERSION, "Invalid version");
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

@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record InventorySlot(
        int slot,
        String id,
        int count
) {}

/* *** Options *** */

@JsonTypeInfo(use = Id.MINIMAL_CLASS, include = As.PROPERTY, property = MCioConfig.MCIO_TYPE)
interface Option { }

/* Organize updates by category */
@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record StatCategory (
        String category,
        ArrayList<Stat> stats
) {}
@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record Stat (
        String id,
        int value
) {}
@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record StatsUpdateOption(
        ArrayList<StatCategory> categories
) implements Option {}

@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record StatsFullOption(
        ArrayList<StatCategory> categories
) implements Option {}

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
 *
 * Note: Jackson doesn't seem to actually need the type annotation to decode. I think it only needs it
 * for abstract types like Option. Python does need the annotations to decode.
 */
@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record ActionPacket(
        // Control
        int version,    // MCIO_PROTOCOL_VERSION
        int sequence,
        String[] commands,  // Server commands to execute (teleport, time set, etc.). Do not include the /
        boolean clear_input, // clear all key / button presses
        boolean stop,   // Tell Minecraft to exit

        // Action
        InputEvent[] inputs,          // Array of key/mouse button inputs
        // Array of length 1 of (xpos, ypos) pairs. Array just for consistency.
        // Also, the list makes it easy to leave empty.
        double[][] cursor_pos,
        ArrayList<Option> options   // Future use
) {
    // Helper for debugging to print the double arrays nicely
    public String arrayToString(int[][] array) {
        return Arrays.deepToString(array);
    }
}

enum InputType {
    KEY,    // 0
    MOUSE   // 1
}

@JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.PROPERTY, property=MCioConfig.MCIO_TYPE)
record InputEvent(
        InputType type,
        int code,   // GLFW key/button code, e.g. GLFW.GLFW_KEY_LEFT_SHIFT or GLFW.GLFW_MOUSE_BUTTON_LEFT
        int action  // GLFW.GLFW_RELEASE or GLFW.GLFW_PRESS
) implements Option {}

/* Deserialize ActionPacket */
class ActionPacketUnpacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());
    private static final ObjectMapper DEBUG_MAPPER = new ObjectMapper().enable(
            SerializationFeature.INDENT_OUTPUT);

    public static Optional<ActionPacket> unpack(byte[] data) {
        try {
            ActionPacket actionPacket = CBOR_MAPPER.readValue(data, ActionPacket.class);
//            LOGGER.info("ACTION\n{}", actionPacket);
            if (actionPacket == null) {
                LOGGER.error("Unpacked action packet is null");
                return Optional.empty();
            }
            if (actionPacket.version() != MCioConfig.MCIO_PROTOCOL_VERSION) {
                LOGGER.error("MCIO_PROTOCOL_VERSION mismatch: got {}, expected {}",
                        actionPacket.version(), MCioConfig.MCIO_PROTOCOL_VERSION);
                return Optional.empty();
            }
            return Optional.of(actionPacket);
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

/* Serialize ActionPacket (for testing) */
class ActionPacketPacker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ObjectMapper CBOR_MAPPER = new ObjectMapper(new CBORFactory());

    public static byte[] pack(ActionPacket action) throws IOException {
        return CBOR_MAPPER.writeValueAsBytes(action);
    }
}
