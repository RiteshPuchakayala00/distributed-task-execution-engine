package com.engine.common.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a protocol message exchanged between master and worker nodes.
 *
 * <p>Each message has a {@link MessageType}, a unique correlation ID for request-response
 * matching, a timestamp, and an optional binary payload. Messages are constructed via
 * the {@link Builder} and are fully immutable.</p>
 *
 * @author Engine Team
 */
public final class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String correlationId;
    private final long timestamp;
    private final byte[] payload;

    /**
     * Private constructor — use {@link Builder} to create instances.
     *
     * @param builder the builder containing the field values
     */
    private Message(Builder builder) {
        this.type = builder.type;
        this.correlationId = builder.correlationId;
        this.timestamp = builder.timestamp;
        this.payload = builder.payload != null ? Arrays.copyOf(builder.payload, builder.payload.length) : null;
    }

    /**
     * Returns the type of this message.
     *
     * @return the message type
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Returns the correlation ID used to match requests with their responses.
     *
     * @return the correlation ID (UUID string)
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns the epoch-millisecond timestamp when this message was created.
     *
     * @return the creation timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a defensive copy of the message payload.
     *
     * <p>If no payload was set, an empty byte array is returned.</p>
     *
     * @return a copy of the payload, or an empty byte array if payload is {@code null}
     */
    public byte[] getPayload() {
        if (payload == null) {
            return new byte[0];
        }
        return Arrays.copyOf(payload, payload.length);
    }

    /**
     * Checks whether this message carries a non-empty payload.
     *
     * @return {@code true} if the payload is not {@code null} and has a length greater than zero
     */
    public boolean hasPayload() {
        return payload != null && payload.length > 0;
    }

    /**
     * Two messages are considered equal if they share the same {@code correlationId}.
     *
     * @param o the reference object with which to compare
     * @return {@code true} if the other object is a {@code Message} with the same correlation ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(correlationId, message.correlationId);
    }

    /**
     * Returns a hash code based solely on the {@code correlationId}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(correlationId);
    }

    /**
     * Returns a concise string representation including message type, correlation ID,
     * timestamp, and payload size.
     *
     * @return a human-readable string
     */
    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", correlationId='" + correlationId + '\'' +
                ", timestamp=" + timestamp +
                ", payloadSize=" + (payload != null ? payload.length : 0) +
                '}';
    }

    /**
     * Builder for constructing {@link Message} instances.
     *
     * <p>The primary constructor requires a {@link MessageType} and automatically
     * generates a UUID correlation ID and sets the timestamp to the current time.</p>
     *
     * @author Engine Team
     */
    public static final class Builder {

        private MessageType type;
        private String correlationId;
        private long timestamp;
        private byte[] payload;

        /**
         * Creates a new builder for a message of the given type.
         *
         * <p>Automatically generates a UUID for the correlation ID and sets the
         * timestamp to {@link System#currentTimeMillis()}.</p>
         *
         * @param type the message type; must not be {@code null}
         */
        public Builder(MessageType type) {
            this.type = type;
            this.correlationId = UUID.randomUUID().toString();
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Creates a new builder pre-populated with all fields from an existing message.
         *
         * @param msg the message to copy values from
         */
        public Builder(Message msg) {
            this.type = msg.type;
            this.correlationId = msg.correlationId;
            this.timestamp = msg.timestamp;
            this.payload = msg.payload != null ? Arrays.copyOf(msg.payload, msg.payload.length) : null;
        }

        /**
         * Sets the message payload (defensive copy is made).
         *
         * @param payload the payload byte array, or {@code null}
         * @return this builder
         */
        public Builder payload(byte[] payload) {
            this.payload = payload != null ? Arrays.copyOf(payload, payload.length) : null;
            return this;
        }

        /**
         * Sets the correlation ID.
         *
         * @param correlationId the correlation ID
         * @return this builder
         */
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Sets the timestamp (epoch milliseconds).
         *
         * @param timestamp the timestamp
         * @return this builder
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds and returns an immutable {@link Message} instance.
         *
         * @return the constructed message
         * @throws IllegalStateException if {@code type} or {@code correlationId} is {@code null}
         */
        public Message build() {
            if (type == null) {
                throw new IllegalStateException("type must not be null");
            }
            if (correlationId == null) {
                throw new IllegalStateException("correlationId must not be null");
            }
            return new Message(this);
        }
    }
}
