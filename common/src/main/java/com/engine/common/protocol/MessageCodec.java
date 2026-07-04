package com.engine.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.exception.ProtocolException;
import com.engine.common.exception.SerializationException;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;

/**
 * Codec responsible for encoding {@link Message} objects to a length-prefixed binary
 * format and decoding them back from a stream.
 *
 * <p>The wire format consists of a 4-byte big-endian integer indicating the payload
 * length, followed by the serialized message bytes. This framing allows the receiver
 * to read exactly the correct number of bytes for each message.</p>
 *
 * <p>Message size is bounded by {@link ProtocolConstants#MAX_MESSAGE_SIZE_BYTES} to
 * prevent out-of-memory attacks from malicious or malformed data.</p>
 *
 * @author Engine Team
 */
public final class MessageCodec {

    private static final Logger logger = LoggerFactory.getLogger(MessageCodec.class);

    private final Serializer serializer;

    /**
     * Constructs a {@code MessageCodec} with the given serializer.
     *
     * @param serializer the serializer to use for encoding/decoding messages; must not be {@code null}
     * @throws NullPointerException if {@code serializer} is {@code null}
     */
    public MessageCodec(Serializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * Creates a {@code MessageCodec} using the default {@link JavaSerializer}.
     *
     * @return a new {@code MessageCodec} with Java serialization
     */
    public static MessageCodec createDefault() {
        return new MessageCodec(new JavaSerializer());
    }

    /**
     * Encodes a {@link Message} and writes it to the given output stream.
     *
     * <p>The message is serialized to bytes, prefixed with a 4-byte length header,
     * and flushed to the stream.</p>
     *
     * @param message the message to encode; must not be {@code null}
     * @param out     the data output stream to write to; must not be {@code null}
     * @throws IOException       if an I/O error occurs while writing
     * @throws ProtocolException if serialization fails or the serialized message exceeds
     *                           {@link ProtocolConstants#MAX_MESSAGE_SIZE_BYTES}
     */
    public void encode(Message message, DataOutputStream out) throws IOException {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(out, "output stream must not be null");

        byte[] bytes;
        try {
            bytes = serializer.serialize(message);
        } catch (SerializationException e) {
            throw new ProtocolException("Failed to serialize message: " + message.getType(), e);
        }

        if (bytes.length > ProtocolConstants.MAX_MESSAGE_SIZE_BYTES) {
            throw new ProtocolException(
                    "Serialized message size (" + bytes.length + " bytes) exceeds maximum allowed size ("
                            + ProtocolConstants.MAX_MESSAGE_SIZE_BYTES + " bytes)");
        }

        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();

        logger.debug("Encoded {} message ({} bytes), correlationId={}",
                message.getType(), bytes.length, message.getCorrelationId());
    }

    /**
     * Reads and decodes a {@link Message} from the given input stream.
     *
     * <p>Reads a 4-byte length prefix, then reads exactly that many bytes and
     * deserializes them into a {@code Message}.</p>
     *
     * @param in the data input stream to read from; must not be {@code null}
     * @return the decoded message
     * @throws IOException       if an I/O error occurs while reading
     * @throws ProtocolException if the length prefix is invalid, the stream is truncated,
     *                           or deserialization fails
     */
    public Message decode(DataInputStream in) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");

        int length = in.readInt();

        if (length <= 0 || length > ProtocolConstants.MAX_MESSAGE_SIZE_BYTES) {
            throw new ProtocolException("Invalid message length: " + length);
        }

        byte[] data = in.readNBytes(length);

        if (data.length != length) {
            throw new ProtocolException(
                    "Incomplete message: expected " + length + " bytes, got " + data.length);
        }

        Message message;
        try {
            message = serializer.deserialize(data, Message.class);
        } catch (SerializationException e) {
            throw new ProtocolException("Failed to deserialize message", e);
        }

        logger.debug("Decoded {} message ({} bytes), correlationId={}",
                message.getType(), data.length, message.getCorrelationId());

        return message;
    }
}
