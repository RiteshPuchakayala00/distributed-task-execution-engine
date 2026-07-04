package com.engine.master.network;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.protocol.Message;

/**
 * Manages active TCP connections to worker nodes.
 *
 * <p>Thread-safe via a {@link ConcurrentHashMap} keyed by worker ID. Provides
 * targeted and broadcast messaging as well as lifecycle management of
 * connections.</p>
 *
 * @author Engine Team
 */
public class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final ConcurrentHashMap<String, WorkerConnectionHandler> connections = new ConcurrentHashMap<>();

    /**
     * Registers a connection handler for the given worker.
     *
     * @param workerId the unique worker identifier (must not be {@code null})
     * @param handler  the connection handler for this worker (must not be {@code null})
     * @throws NullPointerException if any argument is {@code null}
     */
    public void addConnection(String workerId, WorkerConnectionHandler handler) {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        connections.put(workerId, handler);
        logger.info("Added connection for worker {}. Total connections: {}", workerId, connections.size());
    }

    /**
     * Removes the connection handler for the given worker.
     *
     * @param workerId the unique worker identifier
     */
    public void removeConnection(String workerId) {
        connections.remove(workerId);
        logger.info("Removed connection for worker {}. Remaining connections: {}", workerId, connections.size());
    }

    /**
     * Sends a message to a specific worker.
     *
     * @param workerId the target worker ID
     * @param message  the message to send
     * @throws IOException if the worker is not connected, the connection is inactive,
     *                     or an I/O error occurs during transmission
     */
    public void sendToWorker(String workerId, Message message) throws IOException {
        WorkerConnectionHandler handler = connections.get(workerId);
        if (handler == null) {
            throw new IOException("No connection found for worker: " + workerId);
        }
        if (!handler.isActive()) {
            throw new IOException("Connection to worker " + workerId + " is not active");
        }
        handler.sendMessage(message);
    }

    /**
     * Broadcasts a message to all connected workers.
     *
     * <p>Failures for individual workers are logged at WARN level but do not
     * prevent delivery to remaining workers.</p>
     *
     * @param message the message to broadcast
     */
    public void broadcastMessage(Message message) {
        for (var entry : connections.entrySet()) {
            try {
                entry.getValue().sendMessage(message);
            } catch (IOException e) {
                logger.warn("Failed to send broadcast message to worker {}: {}",
                        entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Returns the number of connections whose handler reports as active.
     *
     * @return count of active connections
     */
    public int getActiveCount() {
        return (int) connections.values().stream()
                .filter(WorkerConnectionHandler::isActive)
                .count();
    }

    /**
     * Checks whether a connection exists for the given worker.
     *
     * @param workerId the worker ID to check
     * @return {@code true} if a connection handler is registered for the worker
     */
    public boolean hasConnection(String workerId) {
        return connections.containsKey(workerId);
    }

    /**
     * Closes all connections and clears the connection map.
     */
    public void closeAll() {
        for (var entry : connections.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                logger.warn("Error closing connection to worker {}: {}", entry.getKey(), e.getMessage());
            }
        }
        connections.clear();
        logger.info("All connections closed");
    }

    /**
     * Returns the set of worker IDs that have active connections.
     *
     * @return an unmodifiable copy of connected worker IDs
     */
    public Set<String> getConnectedWorkerIds() {
        return Set.copyOf(connections.keySet());
    }
}
