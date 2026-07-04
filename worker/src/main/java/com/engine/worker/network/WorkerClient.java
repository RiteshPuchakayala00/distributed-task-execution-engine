package com.engine.worker.network;

import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageCodec;
import com.engine.common.protocol.MessageType;
import com.engine.common.exception.ProtocolException;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.common.model.Task;
import com.engine.common.model.WorkerRegistrationInfo;
import com.engine.worker.config.WorkerConfig;
import com.engine.worker.executor.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles TCP connection to Master.
 *
 * @author Engine Team
 */
public class WorkerClient {

    private static final Logger logger = LoggerFactory.getLogger(WorkerClient.class);

    private final WorkerConfig config;
    private final MessageCodec codec;
    private final Serializer serializer;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile String assignedWorkerId;

    /**
     * Constructs a WorkerClient.
     *
     * @param config       the worker configuration
     * @param taskExecutor the task executor
     */
    public WorkerClient(WorkerConfig config, TaskExecutor taskExecutor) {
        this.config = Objects.requireNonNull(config, "WorkerConfig cannot be null");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "TaskExecutor cannot be null");
        this.codec = MessageCodec.createDefault();
        this.serializer = new JavaSerializer();
    }

    /**
     * Connects and registers with the master.
     *
     * @throws IOException       if an I/O error occurs
     * @throws ProtocolException if a protocol error occurs
     */
    public void connectAndRegister() throws IOException, ProtocolException {
        socket = new Socket(config.getMasterHost(), config.getMasterPort());
        socket.setSoTimeout(config.getSocketTimeoutMs());
        socket.setTcpNoDelay(true);
        
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        WorkerRegistrationInfo registrationInfo = new WorkerRegistrationInfo(null, getHostname(), config.getMasterPort(), config.getCorePoolSize());
        Message registerRequest = new Message.Builder(MessageType.REGISTER_REQUEST)
                .payload(serializer.serialize(registrationInfo))
                .build();
                
        sendMessage(registerRequest);

        Message ackMessage = codec.decode(in);
        if (ackMessage.getType() != MessageType.REGISTER_ACK) {
            throw new ProtocolException("Expected REGISTER_ACK, got " + ackMessage.getType());
        }

        try {
            this.assignedWorkerId = serializer.deserialize(ackMessage.getPayload(), String.class);
        } catch (com.engine.common.exception.SerializationException e) {
            throw new ProtocolException("Failed to deserialize assignedWorkerId", e);
        }
        
        active.set(true);
        logger.info("Registered successfully with ID: {}", assignedWorkerId);
    }

    /**
     * Starts the read loop to receive messages from the master.
     */
    public void startReadLoop() {
        Thread readThread = new Thread(() -> {
            try {
                while (active.get() && !socket.isClosed()) {
                    Message message = codec.decode(in);
                    dispatch(message);
                }
            } catch (EOFException | SocketException e) {
                logger.info("Connection closed by master or socket error: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Error in read loop", e);
            } finally {
                disconnect();
            }
        }, "WorkerReadLoop");
        readThread.setDaemon(true);
        readThread.start();
    }

    /**
     * Dispatches the received message.
     *
     * @param message the message to dispatch
     */
    private void dispatch(Message message) {
        if (message.getType() == MessageType.HEARTBEAT_ACK) {
            logger.trace("Received HEARTBEAT_ACK from master");
        } else if (message.getType() == MessageType.TASK_ASSIGN) {
            handleTaskAssign(message);
        } else {
            logger.warn("Received unexpected message type: {}", message.getType());
        }
    }

    /**
     * Handles task assignment from master.
     *
     * @param message the TASK_ASSIGN message
     */
    private void handleTaskAssign(Message message) {
        try {
            Task task = serializer.deserialize(message.getPayload(), Task.class);
            logger.info("Received TASK_ASSIGN for task {}", task.getTaskId());
            taskExecutor.submit(task, this);
        } catch (Exception e) {
            logger.error("Failed to process TASK_ASSIGN", e);
        }
    }

    /**
     * Sends a message to the master.
     *
     * @param message the message to send
     * @throws IOException if an I/O error occurs
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            codec.encode(message, out);
        }
    }

    /**
     * Disconnects from the master.
     */
    public void disconnect() {
        if (active.compareAndSet(true, false)) {
            logger.info("Disconnecting from master");
            
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
            
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Error closing socket", e);
                }
            }
        }
    }

    /**
     * Gets the assigned worker ID.
     *
     * @return the assigned worker ID
     */
    public String getAssignedWorkerId() {
        return assignedWorkerId;
    }

    /**
     * Checks if the client is active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Helper method to get the local hostname.
     *
     * @return the hostname
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
