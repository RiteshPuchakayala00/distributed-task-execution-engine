package com.engine.worker.network;

import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageCodec;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.worker.config.WorkerConfig;
import com.engine.worker.executor.TaskExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("WorkerClient Tests")
class WorkerClientTest {

    private ServerSocket serverSocket;
    private WorkerClient client;
    private Thread clientThread;
    private MessageCodec codec;
    private Serializer serializer;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0);
        System.setProperty("worker.master.host", "127.0.0.1");
        System.setProperty("worker.master.port", String.valueOf(serverSocket.getLocalPort()));
        WorkerConfig config = WorkerConfig.loadDefault();
        TaskExecutor taskExecutor = new TaskExecutor(config);
        client = new WorkerClient(config, taskExecutor);
        codec = MessageCodec.createDefault();
        serializer = new JavaSerializer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.disconnect();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }
    }

    @Test
    @DisplayName("Connect and Register sends request and sets ID")
    void test_WhenConnectAndRegister_ThenSendsRequestAndSetsId() throws Exception {
        CountDownLatch clientReady = new CountDownLatch(1);
        CountDownLatch serverDone = new CountDownLatch(1);
        
        clientThread = new Thread(() -> {
            try {
                client.connectAndRegister();
                clientReady.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        clientThread.start();

        try (Socket serverSideClient = serverSocket.accept();
             DataOutputStream out = new DataOutputStream(serverSideClient.getOutputStream());
             DataInputStream in = new DataInputStream(serverSideClient.getInputStream())) {

            Message request = codec.decode(in);
            assertEquals(MessageType.REGISTER_REQUEST, request.getType());

            Message response = new Message.Builder(MessageType.REGISTER_ACK)
                    .payload(serializer.serialize("assigned-id-123"))
                    .build();
            codec.encode(response, out);
            
            serverDone.countDown();
        }

        assertTrue(clientReady.await(5, TimeUnit.SECONDS), "Client did not finish registration in time");
        assertTrue(serverDone.await(5, TimeUnit.SECONDS), "Server did not finish in time");

        assertAll(
            () -> assertEquals("assigned-id-123", client.getAssignedWorkerId()),
            () -> assertTrue(client.isActive())
        );
    }

    @Test
    @DisplayName("SendMessage sends message to stream")
    void test_WhenSendMessage_ThenMessageSentToStream() throws Exception {
        CountDownLatch clientReady = new CountDownLatch(1);
        
        clientThread = new Thread(() -> {
            try {
                client.connectAndRegister();
                clientReady.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        clientThread.start();

        try (Socket serverSideClient = serverSocket.accept();
             DataOutputStream out = new DataOutputStream(serverSideClient.getOutputStream());
             DataInputStream in = new DataInputStream(serverSideClient.getInputStream())) {

            codec.decode(in); // Consume REGISTER_REQUEST
            Message response = new Message.Builder(MessageType.REGISTER_ACK)
                    .payload(serializer.serialize("assigned-id-123"))
                    .build();
            codec.encode(response, out);

            assertTrue(clientReady.await(5, TimeUnit.SECONDS));
            
            Message testMsg = new Message.Builder(MessageType.HEARTBEAT).build();
            client.sendMessage(testMsg);

            Message receivedMsg = codec.decode(in);
            assertNotNull(receivedMsg);
            assertEquals(MessageType.HEARTBEAT, receivedMsg.getType());
        }
    }

    @Test
    @DisplayName("Disconnect closes socket")
    void test_WhenDisconnect_ThenSocketClosed() throws Exception {
        CountDownLatch clientReady = new CountDownLatch(1);
        
        clientThread = new Thread(() -> {
            try {
                client.connectAndRegister();
                clientReady.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        clientThread.start();

        Socket serverSideClient = serverSocket.accept();
        DataOutputStream out = new DataOutputStream(serverSideClient.getOutputStream());
        DataInputStream in = new DataInputStream(serverSideClient.getInputStream());

        codec.decode(in); // Consume REGISTER_REQUEST
        Message response = new Message.Builder(MessageType.REGISTER_ACK)
                .payload(serializer.serialize("assigned-id-123"))
                .build();
        codec.encode(response, out);

        assertTrue(clientReady.await(5, TimeUnit.SECONDS));
        assertTrue(client.isActive());

        client.disconnect();
        assertFalse(client.isActive());
        
        serverSideClient.close();
    }
}
