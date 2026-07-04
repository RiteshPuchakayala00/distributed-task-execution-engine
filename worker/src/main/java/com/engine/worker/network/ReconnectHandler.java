package com.engine.worker.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles exponential backoff reconnection to the master.
 *
 * @author Engine Team
 */
public class ReconnectHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReconnectHandler.class);

    private final WorkerClient client;
    private final int maxRetries;
    private final long backoffBaseMs;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * Constructs a ReconnectHandler.
     *
     * @param client        the worker client to reconnect
     * @param maxRetries    maximum number of connection attempts
     * @param backoffBaseMs base delay in ms for exponential backoff
     */
    public ReconnectHandler(WorkerClient client, int maxRetries, long backoffBaseMs) {
        this.client = client;
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
    }

    /**
     * Attempts to reconnect to the master using exponential backoff.
     * Blocks until successful or max retries are exceeded.
     *
     * @return true if successfully reconnected, false otherwise
     */
    public boolean attemptReconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return false; // Already reconnecting
        }

        try {
            int attempt = 1;
            long delay = backoffBaseMs;

            while (attempt <= maxRetries) {
                logger.info("Reconnection attempt {}/{} in {}ms...", attempt, maxRetries, delay);
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                try {
                    client.connectAndRegister();
                    logger.info("Successfully reconnected to master on attempt {}", attempt);
                    return true;
                } catch (Exception e) {
                    logger.warn("Reconnection attempt {} failed: {}", attempt, e.getMessage());
                    attempt++;
                    delay = Math.min(delay * 2, 60000); // Cap at 60s
                }
            }

            logger.error("Failed to reconnect after {} attempts", maxRetries);
            return false;
        } finally {
            reconnecting.set(false);
        }
    }
}
