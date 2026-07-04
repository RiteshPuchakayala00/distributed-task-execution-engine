package com.engine.common.protocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the set of message types used in the master-worker wire protocol.
 *
 * <p>Each message type carries a unique integer code for compact serialization
 * and a human-readable direction indicator showing whether the message flows
 * from Worker to Master ({@code "W→M"}), Master to Worker ({@code "M→W"}),
 * or in both directions ({@code "BOTH"}).</p>
 *
 * @author Engine Team
 */
public enum MessageType {

    /** Worker registration request sent from a worker to the master. */
    REGISTER_REQUEST(1, "W→M"),

    /** Master acknowledgement of a successful worker registration. */
    REGISTER_ACK(2, "M→W"),

    /** Periodic heartbeat sent from a worker to the master. */
    HEARTBEAT(3, "W→M"),

    /** Master acknowledgement of a received heartbeat. */
    HEARTBEAT_ACK(4, "M→W"),

    /** Task assignment sent from the master to a worker. */
    TASK_ASSIGN(5, "M→W"),

    /** Worker acknowledgement of a received task assignment. */
    TASK_ACK(6, "W→M"),

    /** Task execution result sent from a worker to the master. */
    TASK_RESULT(7, "W→M"),

    /** Task cancellation directive sent from the master to a worker. */
    TASK_CANCEL(8, "M→W"),

    /** Graceful shutdown notification sent from a worker to the master. */
    WORKER_SHUTDOWN(9, "W→M"),

    /** Error notification that can originate from either side. */
    ERROR(10, "BOTH");

    private final int code;
    private final String direction;

    /** Immutable lookup map from integer code to {@code MessageType}. */
    private static final Map<Integer, MessageType> CODE_MAP;

    static {
        Map<Integer, MessageType> map = new HashMap<>();
        for (MessageType type : values()) {
            map.put(type.code, type);
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Constructs a {@code MessageType} with the given wire-protocol code and direction.
     *
     * @param code      the unique integer code for this message type
     * @param direction a human-readable direction indicator (e.g. {@code "W→M"})
     */
    MessageType(int code, String direction) {
        this.code = code;
        this.direction = direction;
    }

    /**
     * Returns the unique integer code for this message type.
     *
     * @return the wire-protocol code
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the direction indicator for this message type.
     *
     * <p>Possible values are {@code "W→M"} (Worker to Master),
     * {@code "M→W"} (Master to Worker), or {@code "BOTH"}.</p>
     *
     * @return the direction string
     */
    public String getDirection() {
        return direction;
    }

    /**
     * Resolves a {@code MessageType} from its integer code.
     *
     * @param code the integer code to look up
     * @return the corresponding {@code MessageType}
     * @throws IllegalArgumentException if no {@code MessageType} exists for the given code
     */
    public static MessageType fromCode(int code) {
        MessageType type = CODE_MAP.get(code);
        if (type == null) {
            throw new IllegalArgumentException("Unknown MessageType code: " + code);
        }
        return type;
    }
}
