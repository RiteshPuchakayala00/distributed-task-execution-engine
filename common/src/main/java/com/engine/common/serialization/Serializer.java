package com.engine.common.serialization;

import com.engine.common.exception.SerializationException;

/**
 * Abstraction for serializing objects to byte arrays and deserializing byte arrays
 * back to typed objects.
 *
 * <p>Implementations of this interface provide the underlying codec strategy used
 * by the protocol layer to encode and decode messages on the wire. The default
 * implementation uses Java's built-in object serialization, but alternative
 * implementations (e.g., JSON, Protocol Buffers) can be substituted.</p>
 *
 * @author Engine Team
 */
public interface Serializer {

    /**
     * Serializes the given object into a byte array.
     *
     * @param obj the object to serialize; must not be {@code null}
     * @return a byte array representing the serialized form of the object
     * @throws SerializationException if the object cannot be serialized
     */
    byte[] serialize(Object obj) throws SerializationException;

    /**
     * Deserializes a byte array into an object of the specified type.
     *
     * @param data the byte array to deserialize; must not be {@code null} or empty
     * @param type the expected class of the deserialized object
     * @param <T>  the target type
     * @return the deserialized object
     * @throws SerializationException if the data cannot be deserialized or does not
     *                                match the expected type
     */
    <T> T deserialize(byte[] data, Class<T> type) throws SerializationException;
}
