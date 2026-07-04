package com.engine.common.serialization;

import com.engine.common.exception.SerializationException;
import com.engine.common.model.Task;
import com.engine.common.model.TaskPriority;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JavaSerializer Tests")
class JavaSerializerTest {

    private JavaSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JavaSerializer();
    }

    @Nested
    @DisplayName("Serialization Roundtrip")
    class RoundtripTests {

        @Test
        @DisplayName("Serialize and deserialize roundtrip preserves String value")
        void test_WhenStringSerializedAndDeserialized_ThenValueIsPreserved() {
            String original = "Hello, Distributed World!";

            byte[] bytes = serializer.serialize(original);
            String deserialized = serializer.deserialize(bytes, String.class);

            assertEquals(original, deserialized,
                    "String should be preserved after serialize/deserialize roundtrip");
        }

        @Test
        @DisplayName("Serialize and deserialize roundtrip preserves Task object")
        void test_WhenTaskSerializedAndDeserialized_ThenAllFieldsArePreserved() {
            Task original = new Task.Builder()
                    .taskType("compute")
                    .priority(TaskPriority.HIGH)
                    .maxRetries(5)
                    .timeoutMs(30000L)
                    .payload(new byte[]{1, 2, 3})
                    .build();

            byte[] bytes = serializer.serialize(original);
            Task deserialized = serializer.deserialize(bytes, Task.class);

            assertAll("task roundtrip",
                    () -> assertNotNull(deserialized),
                    () -> assertEquals(original.getTaskId(), deserialized.getTaskId()),
                    () -> assertEquals(original.getTaskType(), deserialized.getTaskType()),
                    () -> assertEquals(original.getPriority(), deserialized.getPriority()),
                    () -> assertEquals(original.getMaxRetries(), deserialized.getMaxRetries()),
                    () -> assertEquals(original.getTimeoutMs(), deserialized.getTimeoutMs())
            );
        }
    }

    @Nested
    @DisplayName("Serialization Error Handling")
    class SerializationErrors {

        @Test
        @DisplayName("Serialize null throws SerializationException or NullPointerException")
        void test_WhenSerializingNull_ThenThrowsException() {
            assertThrows(Exception.class, () -> serializer.serialize(null),
                    "Serializing null should throw an exception");
        }
    }

    @Nested
    @DisplayName("Deserialization Error Handling")
    class DeserializationErrors {

        @Test
        @DisplayName("Deserialize empty byte array throws SerializationException")
        void test_WhenDeserializingEmptyArray_ThenThrowsSerializationException() {
            assertThrows(SerializationException.class,
                    () -> serializer.deserialize(new byte[0], String.class),
                    "Deserializing empty byte array should throw SerializationException");
        }

        @Test
        @DisplayName("Deserialize null data throws NullPointerException")
        void test_WhenDeserializingNull_ThenThrowsNullPointerException() {
            assertThrows(NullPointerException.class,
                    () -> serializer.deserialize(null, String.class),
                    "Deserializing null data should throw NullPointerException");
        }

        @Test
        @DisplayName("Deserialize with wrong target type throws SerializationException")
        void test_WhenDeserializingWithWrongType_ThenThrowsSerializationException() {
            String original = "test string";
            byte[] bytes = serializer.serialize(original);

            assertThrows(SerializationException.class,
                    () -> serializer.deserialize(bytes, Integer.class),
                    "Deserializing a String as Integer should throw SerializationException");
        }

        @Test
        @DisplayName("Deserialize corrupted data throws SerializationException")
        void test_WhenDeserializingCorruptedData_ThenThrowsSerializationException() {
            byte[] corrupted = {0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE, (byte) 0xFD};

            assertThrows(SerializationException.class,
                    () -> serializer.deserialize(corrupted, String.class),
                    "Deserializing corrupted data should throw SerializationException");
        }
    }
}
