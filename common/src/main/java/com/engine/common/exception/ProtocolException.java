package com.engine.common.exception;

import java.io.Serial;

/**
 * Unchecked exception thrown when a protocol-level error is encountered.
 *
 * <p>This includes invalid message lengths, messages exceeding the maximum allowed
 * size, unknown message type codes, and any other violations of the wire protocol
 * contract between master and worker nodes.</p>
 *
 * @author Engine Team
 */
public class ProtocolException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code ProtocolException} with the specified detail message.
     *
     * @param message the detail message describing the protocol violation
     */
    public ProtocolException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ProtocolException} with the specified detail message and cause.
     *
     * @param message the detail message describing the protocol violation
     * @param cause   the underlying cause of the protocol error
     */
    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
