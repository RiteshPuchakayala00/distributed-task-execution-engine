package com.engine.master.network;

import java.io.IOException;
import java.util.Set;

import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionManager — connection tracking and message dispatch")
@ExtendWith(MockitoExtension.class)
class ConnectionManagerTest {

    private ConnectionManager connectionManager;

    @Mock
    private WorkerConnectionHandler handler1;

    @Mock
    private WorkerConnectionHandler handler2;

    @BeforeEach
    void setUp() {
        connectionManager = new ConnectionManager();
    }

    private Message createTestMessage() {
        return new Message.Builder(MessageType.HEARTBEAT_ACK).build();
    }

    @Nested
    @DisplayName("Connection tracking")
    class ConnectionTracking {

        @Test
        @DisplayName("Add connection increases active count")
        void test_WhenConnectionAdded_ThenCountIncreases() {
            when(handler1.isActive()).thenReturn(true);
            connectionManager.addConnection("w1", handler1);

            assertEquals(1, connectionManager.getActiveCount(),
                    "active count should be 1 after adding one connection");
        }

        @Test
        @DisplayName("Remove connection decreases active count")
        void test_WhenConnectionRemoved_ThenCountDecreases() {
            connectionManager.addConnection("w1", handler1);
            connectionManager.removeConnection("w1");

            assertEquals(0, connectionManager.getActiveCount(),
                    "active count should be 0 after removal");
        }

        @Test
        @DisplayName("hasConnection returns true after add")
        void test_WhenConnectionExists_ThenHasConnectionTrue() {
            connectionManager.addConnection("w1", handler1);
            assertTrue(connectionManager.hasConnection("w1"));
        }

        @Test
        @DisplayName("hasConnection returns false after remove")
        void test_WhenConnectionRemoved_ThenHasConnectionFalse() {
            connectionManager.addConnection("w1", handler1);
            connectionManager.removeConnection("w1");

            assertFalse(connectionManager.hasConnection("w1"));
        }

        @Test
        @DisplayName("getConnectedWorkerIds returns correct set")
        void test_WhenMultipleConnections_ThenIdsReturnedCorrectly() {
            connectionManager.addConnection("w1", handler1);
            connectionManager.addConnection("w2", handler2);

            Set<String> ids = connectionManager.getConnectedWorkerIds();

            assertAll("connected ids",
                () -> assertEquals(2, ids.size()),
                () -> assertTrue(ids.contains("w1")),
                () -> assertTrue(ids.contains("w2"))
            );
        }
    }

    @Nested
    @DisplayName("Message sending")
    class MessageSending {

        @Test
        @DisplayName("sendToWorker calls handler.sendMessage for active handler")
        void test_WhenSendToActiveWorker_ThenHandlerSendMessageCalled() throws IOException {
            when(handler1.isActive()).thenReturn(true);
            connectionManager.addConnection("w1", handler1);

            Message msg = createTestMessage();
            connectionManager.sendToWorker("w1", msg);

            verify(handler1).sendMessage(msg);
        }

        @Test
        @DisplayName("sendToWorker throws IOException for unknown worker")
        void test_WhenSendToUnknownWorker_ThenThrowsIOException() {
            Message msg = createTestMessage();

            assertThrows(IOException.class,
                () -> connectionManager.sendToWorker("unknown", msg),
                "sending to unknown worker should throw IOException");
        }

        @Test
        @DisplayName("sendToWorker throws IOException for inactive handler")
        void test_WhenSendToInactiveWorker_ThenThrowsIOException() {
            when(handler1.isActive()).thenReturn(false);
            connectionManager.addConnection("w1", handler1);

            Message msg = createTestMessage();

            assertThrows(IOException.class,
                () -> connectionManager.sendToWorker("w1", msg),
                "sending to inactive worker should throw IOException");
        }

        @Test
        @DisplayName("broadcastMessage sends to all active connections")
        void test_WhenBroadcast_ThenAllActiveHandlersReceive() throws IOException {
            connectionManager.addConnection("w1", handler1);
            connectionManager.addConnection("w2", handler2);

            Message msg = createTestMessage();
            connectionManager.broadcastMessage(msg);

            assertAll("broadcast",
                () -> verify(handler1).sendMessage(msg),
                () -> verify(handler2).sendMessage(msg)
            );
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        @DisplayName("closeAll closes all handlers and clears map")
        void test_WhenCloseAll_ThenAllHandlersClosedAndMapCleared() {
            connectionManager.addConnection("w1", handler1);
            connectionManager.addConnection("w2", handler2);

            connectionManager.closeAll();

            assertAll("closeAll",
                () -> verify(handler1).close(),
                () -> verify(handler2).close(),
                () -> assertEquals(0, connectionManager.getActiveCount(),
                        "active count should be 0 after closeAll"),
                () -> assertFalse(connectionManager.hasConnection("w1")),
                () -> assertFalse(connectionManager.hasConnection("w2"))
            );
        }
    }
}
