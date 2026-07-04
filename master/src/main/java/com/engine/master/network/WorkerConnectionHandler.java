package com.engine.master.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.exception.ProtocolException;
import com.engine.common.model.WorkerRegistrationInfo;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageCodec;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.registry.WorkerStatus;
import com.engine.master.task.TaskManager;
import com.engine.common.model.TaskResult;

/**
 * Runnable that handles bidirectional communication with a single connected
 * worker node.
 *
 * <p>On startup the handler expects a {@link MessageType#REGISTER_REQUEST} as
 * the first message. After successful registration it enters a read loop,
 * dispatching incoming messages to the appropriate handler method.</p>
 *
 * <p>Write access to the output stream is serialized via an internal write
 * lock so that concurrent calls to {@link #sendMessage(Message)} are safe.</p>
 *
 * @author Engine Team
 */
public class WorkerConnectionHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WorkerConnectionHandler.class);

    private final Socket socket;
    private final MessageCodec codec;
    private final ConnectionManager connectionManager;
    private final WorkerRegistry workerRegistry;
    private final TaskManager taskManager;
    private final Serializer serializer;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Object writeLock = new Object();

    private DataInputStream in;
    private DataOutputStream out;
    private volatile String workerId;

    /**
     * Creates a handler for the given socket.
     *
     * @param socket            the connected worker socket (must not be {@code null})
     * @param codec             the message codec (must not be {@code null})
     * @param connectionManager the connection manager (must not be {@code null})
     * @param workerRegistry    the worker registry (must not be {@code null})
     * @throws NullPointerException if any parameter is {@code null}
     */
    public WorkerConnectionHandler(Socket socket, MessageCodec codec,
                                   ConnectionManager connectionManager,
                                   WorkerRegistry workerRegistry,
                                   TaskManager taskManager) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry must not be null");
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager must not be null");
        this.serializer = new JavaSerializer();
    }

    /**
     * Entry point for the handler thread. Performs registration handshake then
     * enters the main message read loop until the connection is closed or an
     * error occurs.
     */
    @Override
    public void run() {
        Thread.currentThread().setName("worker-handler-" + socket.getRemoteSocketAddress());
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // First message MUST be REGISTER_REQUEST
            Message firstMsg = codec.decode(in);
            if (firstMsg.getType() != MessageType.REGISTER_REQUEST) {
                logger.warn("Expected REGISTER_REQUEST but got {}. Closing connection.", firstMsg.getType());
                return;
            }
            handleRegistration(firstMsg);

            // Main message read loop
            while (active.get() && !Thread.currentThread().isInterrupted()) {
                Message message = codec.decode(in);
                dispatchMessage(message);
            }
        } catch (EOFException e) {
            logger.info("Worker {} disconnected (EOF)", workerId);
        } catch (SocketTimeoutException e) {
            logger.warn("Worker {} socket read timeout", workerId);
        } catch (SocketException e) {
            if (active.get()) {
                logger.warn("Socket error for worker {}: {}", workerId, e.getMessage());
            }
        } catch (IOException e) {
            if (active.get()) {
                logger.error("I/O error communicating with worker {}", workerId, e);
            }
        } catch (ProtocolException e) {
            logger.error("Protocol error from worker {}: {}", workerId, e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Dispatches an incoming message to the appropriate handler based on its type.
     *
     * @param message the incoming message
     * @throws IOException if sending a response fails
     */
    private void dispatchMessage(Message message) throws IOException {
        switch (message.getType()) {
            case HEARTBEAT -> handleHeartbeat(message);
            case TASK_ACK -> handleTaskAck(message);
            case TASK_RESULT -> handleTaskResult(message);
            case WORKER_SHUTDOWN -> handleShutdown(message);
            default -> logger.warn("Unexpected message type from worker {}: {}", workerId, message.getType());
        }
    }

    /**
     * Handles the initial registration handshake.
     *
     * @param message the REGISTER_REQUEST message
     * @throws IOException if sending the ACK fails
     */
    private void handleRegistration(Message message) throws IOException {
        WorkerRegistrationInfo regInfo = serializer.deserialize(message.getPayload(), WorkerRegistrationInfo.class);

        this.workerId = regInfo.workerId() != null ? regInfo.workerId() : UUID.randomUUID().toString();
        Thread.currentThread().setName("worker-handler-" + workerId);

        WorkerInfo workerInfo = new WorkerInfo(
                workerId, regInfo.hostname(), regInfo.port(), regInfo.availableCores());

        workerRegistry.registerWorker(workerInfo);
        connectionManager.addConnection(workerId, this);

        byte[] ackPayload = serializer.serialize(workerId);
        Message ack = new Message.Builder(MessageType.REGISTER_ACK)
                .correlationId(message.getCorrelationId())
                .payload(ackPayload)
                .build();
        sendMessage(ack);

        logger.info("Worker registered: workerId={}, hostname={}, port={}, cores={}",
                workerId, regInfo.hostname(), regInfo.port(), regInfo.availableCores());
    }

    /**
     * Handles a heartbeat message by updating the registry and sending an ACK.
     *
     * @param message the HEARTBEAT message
     * @throws IOException if sending the ACK fails
     */
    private void handleHeartbeat(Message message) throws IOException {
        workerRegistry.updateHeartbeat(workerId);

        Message ack = new Message.Builder(MessageType.HEARTBEAT_ACK)
                .correlationId(message.getCorrelationId())
                .build();
        sendMessage(ack);

        logger.trace("Heartbeat processed for worker {}", workerId);
    }

    /**
     * Handles a task acknowledgement message. Stub for Milestone 4.
     *
     * @param message the TASK_ACK message
     */
    private void handleTaskAck(Message message) {
        logger.debug("Received TASK_ACK from worker {}: correlationId={}", workerId, message.getCorrelationId());
        // Stub for M4
    }

    /**
     * Handles a task result message.
     *
     * @param message the TASK_RESULT message
     */
    private void handleTaskResult(Message message) {
        logger.debug("Received TASK_RESULT from worker {}: correlationId={}", workerId, message.getCorrelationId());
        try {
            TaskResult result = serializer.deserialize(message.getPayload(), TaskResult.class);
            taskManager.completeTask(result);
            
            workerRegistry.getWorker(workerId).ifPresent(worker -> {
                worker.decrementLoad();
                if (worker.getCurrentLoad() < worker.getAvailableCores()) {
                    workerRegistry.compareAndSetStatus(workerId, WorkerStatus.BUSY, WorkerStatus.IDLE);
                }
            });
            
            logger.info("Processed result for task {} from worker {}", result.getTaskId(), workerId);
        } catch (Exception e) {
            logger.error("Failed to process task result from worker {}", workerId, e);
        }
    }

    /**
     * Handles a worker shutdown request by deactivating the handler.
     *
     * @param message the WORKER_SHUTDOWN message
     */
    private void handleShutdown(Message message) {
        logger.info("Worker {} requesting graceful shutdown", workerId);
        active.set(false);
    }

    /**
     * Sends a message to the connected worker. Thread-safe via an internal
     * write lock.
     *
     * @param message the message to send
     * @throws IOException if an I/O error occurs during transmission
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            codec.encode(message, out);
        }
    }

    /**
     * Closes this handler by deactivating it and closing the underlying socket.
     */
    public void close() {
        active.set(false);
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Error closing socket for worker {}: {}", workerId, e.getMessage());
        }
    }

    /**
     * Performs cleanup when the handler's read loop exits.
     */
    private void cleanup() {
        active.set(false);
        if (workerId != null) {
            connectionManager.removeConnection(workerId);
            workerRegistry.deregisterWorker(workerId);
        }
        if (in != null) {
            try { in.close(); } catch (IOException ignored) {}
        }
        if (out != null) {
            try { out.close(); } catch (IOException ignored) {}
        }
        try {
            socket.close();
        } catch (IOException e) {
            // Socket may already be closed; ignore
        }
        logger.info("Handler cleaned up for worker {}", workerId);
    }

    /**
     * Returns the worker ID assigned during registration, or {@code null} if
     * registration has not yet completed.
     *
     * @return the worker ID, or {@code null}
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Returns whether this handler is active and the socket is open.
     *
     * @return {@code true} if the handler is active and the socket is not closed
     */
    public boolean isActive() {
        return active.get() && !socket.isClosed();
    }
}
