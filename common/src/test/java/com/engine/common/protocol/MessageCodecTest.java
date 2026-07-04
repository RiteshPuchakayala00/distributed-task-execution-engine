package com.engine.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.engine.common.exception.ProtocolException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MessageCodec Protocol Tests")
class MessageCodecTest {

    private MessageCodec codec;

    @BeforeEach
    void setUp() {
        codec = MessageCodec.createDefault();
    }

    private byte[] encode(Message message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        codec.encode(message, dos);
        dos.flush();
        return baos.toByteArray();
    }

    private Message decode(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        return codec.decode(dis);
    }

    @Nested
    @DisplayName("Encode-Decode Roundtrip")
    class RoundtripTests {

        @Test
        @DisplayName("Encode then decode roundtrip preserves message fields")
        void test_WhenMessageEncodedAndDecoded_ThenFieldsArePreserved() throws IOException {
            Message original = new Message.Builder(MessageType.HEARTBEAT).build();

            byte[] encoded = encode(original);
            Message decoded = decode(encoded);

            assertAll("roundtrip preservation",
                    () -> assertEquals(original.getType(), decoded.getType()),
                    () -> assertEquals(original.getCorrelationId(), decoded.getCorrelationId()),
                    () -> assertEquals(original.getTimestamp(), decoded.getTimestamp())
            );
        }

        @Test
        @DisplayName("Encode then decode with payload preserves payload data")
        void test_WhenMessageWithPayloadEncodedAndDecoded_ThenPayloadIsPreserved() throws IOException {
            byte[] payload = {10, 20, 30, 40, 50};
            Message original = new Message.Builder(MessageType.TASK_ASSIGN)
                    .payload(payload)
                    .build();

            byte[] encoded = encode(original);
            Message decoded = decode(encoded);

            assertAll("payload roundtrip",
                    () -> assertTrue(decoded.hasPayload(), "Decoded message should have payload"),
                    () -> assertArrayEquals(payload, decoded.getPayload(), "Payload should be preserved")
            );
        }

        @ParameterizedTest(name = "MessageType.{0}")
        @EnumSource(MessageType.class)
        @DisplayName("Encode then decode roundtrip works for all MessageTypes")
        void test_WhenAnyMessageTypeEncodedAndDecoded_ThenTypeIsPreserved(MessageType type) throws IOException {
            Message original = new Message.Builder(type).build();

            byte[] encoded = encode(original);
            Message decoded = decode(encoded);

            assertEquals(original.getType(), decoded.getType(),
                    "MessageType should be preserved after encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("Decode Error Conditions")
    class DecodeErrors {

        @Test
        @DisplayName("Decode with negative payload length throws ProtocolException")
        void test_WhenPayloadLengthIsNegative_ThenThrowsProtocolException() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            assertThrows(ProtocolException.class, () -> {
                // Encode a valid message first, then tamper with the bytes
                Message msg = new Message.Builder(MessageType.HEARTBEAT).build();
                byte[] encoded = encode(msg);

                // Tamper: set payload length to negative value
                // We need to find the payload length position and modify it
                // Instead, craft raw bytes with negative length
                ByteArrayOutputStream tampered = new ByteArrayOutputStream();
                DataOutputStream tdos = new DataOutputStream(tampered);
                tdos.writeInt(MessageType.HEARTBEAT.getCode()); // type code
                tdos.writeUTF(msg.getCorrelationId());          // correlationId
                tdos.writeLong(msg.getTimestamp());              // timestamp
                tdos.writeInt(-1);                               // negative payload length
                tdos.flush();

                decode(tampered.toByteArray());
            });
        }

        @Test
        @DisplayName("Decode with oversized payload length throws ProtocolException")
        void test_WhenPayloadLengthIsOversized_ThenThrowsProtocolException() {
            assertThrows(ProtocolException.class, () -> {
                Message msg = new Message.Builder(MessageType.HEARTBEAT).build();

                ByteArrayOutputStream tampered = new ByteArrayOutputStream();
                DataOutputStream tdos = new DataOutputStream(tampered);
                tdos.writeInt(MessageType.HEARTBEAT.getCode());
                tdos.writeUTF(msg.getCorrelationId());
                tdos.writeLong(msg.getTimestamp());
                tdos.writeInt(100_000_000); // oversized payload length
                tdos.flush();

                decode(tampered.toByteArray());
            });
        }

        @Test
        @DisplayName("Decode with truncated data throws exception")
        void test_WhenDataIsTruncated_ThenThrowsException() {
            assertThrows(Exception.class, () -> {
                // Only write partial data — just a couple of bytes
                byte[] truncated = {0, 0};
                decode(truncated);
            });
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        @DisplayName("Encode null message throws NullPointerException")
        void test_WhenEncodingNullMessage_ThenThrowsNullPointerException() {
            assertThrows(NullPointerException.class, () -> {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                codec.encode(null, dos);
            });
        }

        @Test
        @DisplayName("Decode null input throws NullPointerException")
        void test_WhenDecodingNullInput_ThenThrowsNullPointerException() {
            assertThrows(NullPointerException.class, () -> codec.decode(null));
        }
    }

    @Nested
    @DisplayName("Sequential Multi-Message Stream")
    class SequentialStream {

        @Test
        @DisplayName("Multiple messages encoded and decoded sequentially in the same stream")
        void test_WhenMultipleMessagesWrittenToSameStream_ThenAllAreDecodedCorrectly() throws IOException {
            Message msg1 = new Message.Builder(MessageType.HEARTBEAT).build();
            Message msg2 = new Message.Builder(MessageType.TASK_ASSIGN)
                    .payload(new byte[]{1, 2, 3})
                    .build();
            Message msg3 = new Message.Builder(MessageType.TASK_RESULT)
                    .payload(new byte[]{4, 5, 6, 7})
                    .build();

            // Encode all messages into one stream
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            codec.encode(msg1, dos);
            codec.encode(msg2, dos);
            codec.encode(msg3, dos);
            dos.flush();

            // Decode all messages from the same stream
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(bais);
            Message decoded1 = codec.decode(dis);
            Message decoded2 = codec.decode(dis);
            Message decoded3 = codec.decode(dis);

            assertAll("sequential decode",
                    () -> assertEquals(msg1.getCorrelationId(), decoded1.getCorrelationId()),
                    () -> assertEquals(MessageType.HEARTBEAT, decoded1.getType()),
                    () -> assertEquals(msg2.getCorrelationId(), decoded2.getCorrelationId()),
                    () -> assertEquals(MessageType.TASK_ASSIGN, decoded2.getType()),
                    () -> assertArrayEquals(new byte[]{1, 2, 3}, decoded2.getPayload()),
                    () -> assertEquals(msg3.getCorrelationId(), decoded3.getCorrelationId()),
                    () -> assertEquals(MessageType.TASK_RESULT, decoded3.getType()),
                    () -> assertArrayEquals(new byte[]{4, 5, 6, 7}, decoded3.getPayload())
            );
        }
    }
}
