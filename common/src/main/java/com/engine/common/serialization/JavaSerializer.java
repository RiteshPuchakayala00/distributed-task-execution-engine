package com.engine.common.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.exception.SerializationException;

/**
 * {@link Serializer} implementation that uses Java's native object serialization.
 *
 * <p>This serializer writes objects via {@link ObjectOutputStream} and reads them
 * back via {@link ObjectInputStream}. It requires that all serialized objects
 * implement {@link java.io.Serializable}.</p>
 *
 * <p>This class is stateless and thread-safe.</p>
 *
 * @author Engine Team
 */
public final class JavaSerializer implements Serializer {

    private static final Logger logger = LoggerFactory.getLogger(JavaSerializer.class);

    /**
     * {@inheritDoc}
     *
     * @throws SerializationException if an I/O error occurs during serialization
     */
    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        Objects.requireNonNull(obj, "Object to serialize must not be null");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(obj);
            oos.flush();

            byte[] result = baos.toByteArray();
            logger.trace("Serialized {} to {} bytes", obj.getClass().getSimpleName(), result.length);
            return result;

        } catch (IOException e) {
            throw new SerializationException(
                    "Failed to serialize object of type: " + obj.getClass().getName(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws SerializationException if the byte array is empty, an I/O error occurs,
     *                                the class cannot be found, or the deserialized object
     *                                does not match the expected type
     */
    @Override
    public <T> T deserialize(byte[] data, Class<T> type) throws SerializationException {
        Objects.requireNonNull(data, "Data to deserialize must not be null");
        Objects.requireNonNull(type, "Target type must not be null");

        if (data.length == 0) {
            throw new SerializationException("Cannot deserialize empty byte array");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             SecureObjectInputStream ois = new SecureObjectInputStream(bais)) {

            Object obj = ois.readObject();

            if (!type.isInstance(obj)) {
                throw new SerializationException(
                        "Expected " + type.getName() + " but got " + obj.getClass().getName());
            }

            @SuppressWarnings("unchecked")
            T result = (T) obj;

            logger.trace("Deserialized {} from {} bytes", type.getSimpleName(), data.length);
            return result;

        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException(
                    "Failed to deserialize object of type: " + type.getName(), e);
        }
    }
}
