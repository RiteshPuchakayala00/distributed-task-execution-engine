package com.engine.worker.network;

import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.common.model.HeartbeatPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Runnable that periodically sends a HEARTBEAT message.
 *
 * @author Engine Team
 */
public class HeartbeatTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatTask.class);

    private final WorkerClient client;
    private final Serializer serializer;
    private final long startTimeMs = System.currentTimeMillis();

    /**
     * Constructs a HeartbeatTask.
     *
     * @param client the worker client
     */
    public HeartbeatTask(WorkerClient client) {
        this.client = Objects.requireNonNull(client, "WorkerClient cannot be null");
        this.serializer = new JavaSerializer();
    }

    /**
     * Executes the heartbeat task.
     */
    @Override
    public void run() {
        if (!client.isActive() || client.getAssignedWorkerId() == null) {
            return;
        }

        try {
            long uptime = (System.currentTimeMillis() - startTimeMs) / 1000;
            long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);

            HeartbeatPayload payload = new HeartbeatPayload(client.getAssignedWorkerId(), 0, freeMemory, uptime);
            Message msg = new Message.Builder(MessageType.HEARTBEAT)
                    .payload(serializer.serialize(payload))
                    .build();

            client.sendMessage(msg);
            logger.trace("Sent HEARTBEAT to master");
        } catch (IOException e) {
            logger.warn("Failed to send HEARTBEAT", e);
        }
    }
}
