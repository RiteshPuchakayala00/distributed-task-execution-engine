package com.engine.common.protocol;

/**
 * Defines constants used by the master-worker wire protocol.
 *
 * <p>This utility class centralizes protocol-level configuration values such as
 * the protocol version, maximum message sizes, length-prefix width, and default
 * networking parameters. All fields are compile-time constants.</p>
 *
 * <p>This class cannot be instantiated.</p>
 *
 * @author Engine Team
 */
public final class ProtocolConstants {

    /**
     * Current version of the wire protocol.
     */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * Maximum allowed message size in bytes (10 MB).
     *
     * <p>Messages exceeding this limit will be rejected with a
     * {@link com.engine.common.exception.ProtocolException}.</p>
     */
    public static final int MAX_MESSAGE_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * Number of bytes used for the length prefix in the framing protocol.
     *
     * <p>Each message on the wire is preceded by a 4-byte big-endian integer
     * indicating the number of bytes in the serialized message body.</p>
     */
    public static final int LENGTH_PREFIX_BYTES = 4;

    /**
     * Default socket read/write timeout in milliseconds (30 seconds).
     */
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 30_000;

    /**
     * Default TCP connection timeout in milliseconds (5 seconds).
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;

    /**
     * Default hostname for the master node.
     */
    public static final String DEFAULT_HOST = "localhost";

    /**
     * Default TCP port for the master node.
     */
    public static final int DEFAULT_PORT = 9090;

    /**
     * Private constructor to prevent instantiation.
     */
    private ProtocolConstants() {
        // Utility class — no instances allowed
    }
}
