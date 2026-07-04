package com.engine.master.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import com.engine.common.model.HeartbeatPayload;
import com.engine.common.model.WorkerRegistrationInfo;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageCodec;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@DisplayName("WorkerConnectionHandler — protocol message handling over real sockets")
@ExtendWith(MockitoExtension.class)
@Timeout(5)
class WorkerConnectionHandlerTest {

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private WorkerRegistry workerRegistry;

    @Mock
    private TaskManager taskManager;

    private MessageCodec codec;
    private Socket serverSocket;
    private Socket clientSocket;

    @BeforeEach
    void setUp() {
        codec = MessageCodec.createDefault();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    /**
     * Creates a connected loopback socket pair.
     * Index 0 = server-side socket (given to handler).
     * Index 1 = client-side socket (test's "worker" side).
     */
    private Socket[] createSocketPair() throws IOException {
        ServerSocket server = new ServerSocket(0); // random available port
        Socket client = new Socket("localhost", server.getLocalPort());
        Socket accepted = server.accept();
        server.close();
        return new Socket[]{accepted, client};
    }

    private void sendFromWorker(DataOutputStream out, Message message) throws IOException {
        codec.encode(message, out);
        out.flush();
    }

    private Message readFromMaster(DataInputStream in) throws IOException {
        return codec.decode(in);
    }

    private final JavaSerializer serializer = new JavaSerializer();

    private Message buildRegisterRequest(String workerId, int cores) {
        WorkerRegistrationInfo regInfo = new WorkerRegistrationInfo(
                workerId, "localhost", 9090, cores);
        byte[] payload = serializer.serialize(regInfo);
        return new Message.Builder(MessageType.REGISTER_REQUEST)
                .payload(payload)
                .build();
    }

    private Message buildHeartbeat(String workerId) {
        HeartbeatPayload hb = new HeartbeatPayload(workerId, 2, 1024, 3600);
        byte[] payload = serializer.serialize(hb);
        return new Message.Builder(MessageType.HEARTBEAT)
                .payload(payload)
                .build();
    }

    @Nested
    @DisplayName("Registration protocol")
    class RegistrationProtocol {

        @Test
        @DisplayName("Handler processes REGISTER_REQUEST and sends REGISTER_ACK")
        void test_WhenRegisterRequestReceived_ThenAckSentAndWorkerRegistered() throws Exception {
            Socket[] pair = createSocketPair();
            serverSocket = pair[0];
            clientSocket = pair[1];

            WorkerConnectionHandler handler = new WorkerConnectionHandler(
                    serverSocket, codec, connectionManager, workerRegistry, taskManager);
            Thread handlerThread = new Thread(handler);
            handlerThread.setDaemon(true);
            handlerThread.start();

            Thread.sleep(100); // give handler time to start

            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());

            // Send REGISTER_REQUEST
            sendFromWorker(out, buildRegisterRequest("worker-1", 4));

            // Read REGISTER_ACK
            Message ack = readFromMaster(in);

            assertAll("register ack",
                () -> assertNotNull(ack, "should receive a response"),
                () -> assertEquals(MessageType.REGISTER_ACK, ack.getType(),
                        "response should be REGISTER_ACK")
            );

            // Verify side effects
            verify(workerRegistry, timeout(2000)).registerWorker(any());
            verify(connectionManager, timeout(2000)).addConnection(eq("worker-1"), any());

            // Cleanup
            clientSocket.close();
            handlerThread.join(2000);
        }
    }

    @Nested
    @DisplayName("Heartbeat protocol")
    class HeartbeatProtocol {

        @Test
        @DisplayName("Handler processes HEARTBEAT and sends HEARTBEAT_ACK")
        void test_WhenHeartbeatReceived_ThenAckSentAndHeartbeatUpdated() throws Exception {
            Socket[] pair = createSocketPair();
            serverSocket = pair[0];
            clientSocket = pair[1];

            WorkerConnectionHandler handler = new WorkerConnectionHandler(
                    serverSocket, codec, connectionManager, workerRegistry, taskManager);
            Thread handlerThread = new Thread(handler);
            handlerThread.setDaemon(true);
            handlerThread.start();

            Thread.sleep(100); // give handler time to start

            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());

            // Step 1: Register first
            sendFromWorker(out, buildRegisterRequest("worker-2", 8));
            Message regAck = readFromMaster(in);
            assertEquals(MessageType.REGISTER_ACK, regAck.getType());

            // Step 2: Send heartbeat
            sendFromWorker(out, buildHeartbeat("worker-2"));
            Message hbAck = readFromMaster(in);

            assertAll("heartbeat ack",
                () -> assertNotNull(hbAck),
                () -> assertEquals(MessageType.HEARTBEAT_ACK, hbAck.getType())
            );

            verify(workerRegistry, timeout(2000)).updateHeartbeat("worker-2");

            clientSocket.close();
            handlerThread.join(2000);
        }
    }

    @Nested
    @DisplayName("Protocol violations")
    class ProtocolViolations {

        @Test
        @DisplayName("Non-REGISTER first message causes disconnect")
        void test_WhenFirstMessageNotRegister_ThenConnectionClosed() throws Exception {
            Socket[] pair = createSocketPair();
            serverSocket = pair[0];
            clientSocket = pair[1];

            WorkerConnectionHandler handler = new WorkerConnectionHandler(
                    serverSocket, codec, connectionManager, workerRegistry, taskManager);
            Thread handlerThread = new Thread(handler);
            handlerThread.setDaemon(true);
            handlerThread.start();

            Thread.sleep(100); // give handler time to start

            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            // Send HEARTBEAT as first message (protocol violation)
            sendFromWorker(out, buildHeartbeat("rogue-worker"));

            // Handler should close the connection — wait for thread to finish
            handlerThread.join(3000);

            // Verify no registration happened
            verify(workerRegistry, never()).registerWorker(any());
            verify(connectionManager, never()).addConnection(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("Handler cleans up on worker disconnect")
        void test_WhenWorkerDisconnects_ThenCleanupPerformed() throws Exception {
            Socket[] pair = createSocketPair();
            serverSocket = pair[0];
            clientSocket = pair[1];

            WorkerConnectionHandler handler = new WorkerConnectionHandler(
                    serverSocket, codec, connectionManager, workerRegistry, taskManager);
            Thread handlerThread = new Thread(handler);
            handlerThread.setDaemon(true);
            handlerThread.start();

            Thread.sleep(100); // give handler time to start

            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());

            // Register
            sendFromWorker(out, buildRegisterRequest("worker-dc", 4));
            readFromMaster(in); // consume ACK

            // Disconnect
            clientSocket.close();
            handlerThread.join(3000);

            // Verify deregistration
            verify(connectionManager, timeout(2000)).removeConnection("worker-dc");
        }

        @Test
        @DisplayName("isActive returns true after creation, false after close")
        void test_WhenHandlerCreatedAndClosed_ThenIsActiveReflectsState() throws Exception {
            Socket[] pair = createSocketPair();
            serverSocket = pair[0];
            clientSocket = pair[1];

            WorkerConnectionHandler handler = new WorkerConnectionHandler(
                    serverSocket, codec, connectionManager, workerRegistry, taskManager);

            assertTrue(handler.isActive(), "handler should be active after creation");

            handler.close();

            assertFalse(handler.isActive(), "handler should be inactive after close");
        }
    }
}
