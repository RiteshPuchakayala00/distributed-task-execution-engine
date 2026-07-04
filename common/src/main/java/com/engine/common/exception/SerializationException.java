package com.engine.common.exception;

import java.io.Serial;

/**
 * Unchecked exception thrown when an object cannot be serialized or deserialized.
 *
 * <p>This exception wraps underlying I/O or class-loading failures that occur
 * during the serialization lifecycle, providing a consistent error type for
 * callers to handle without being forced to catch checked exceptions.</p>
 *
 * @author Engine Team
 */
public class SerializationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code SerializationException} with the specified detail message.
     *
     * @param message the detail message describing the serialization failure
     */
    public SerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code SerializationException} with the specified detail message and cause.
     *
     * @param message the detail message describing the serialization failure
     * @param cause   the underlying cause of the serialization failure
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
