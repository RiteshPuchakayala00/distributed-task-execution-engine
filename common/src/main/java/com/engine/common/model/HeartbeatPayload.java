package com.engine.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable data carrier sent by a worker node with each periodic heartbeat.
 *
 * <p>An instance of this record is serialized and embedded as the payload of a
 * {@code HEARTBEAT} message. The master uses the enclosed metrics to track
 * worker liveness, estimate available capacity for scheduling decisions, and
 * detect workers that may be overloaded or running low on memory.
 *
 * <p><b>Invariants enforced by the compact constructor:</b>
 * <ul>
 *   <li>{@code workerId} must not be {@code null}.</li>
 * </ul>
 *
 * @param workerId        the unique identifier of the sending worker
 *                        (must not be {@code null})
 * @param activeTaskCount the number of tasks currently executing on the worker
 * @param freeMemoryMB    the available heap memory on the worker, in megabytes
 * @param uptimeSeconds   the elapsed time since the worker process started,
 *                        in seconds
 *
 * @author Engine Team
 */
public record HeartbeatPayload(
        String workerId,
        int activeTaskCount,
        long freeMemoryMB,
        long uptimeSeconds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Compact constructor that validates the worker identifier.
     *
     * @throws NullPointerException if {@code workerId} is {@code null}
     */
    public HeartbeatPayload {
        Objects.requireNonNull(workerId, "workerId must not be null");
    }
}
