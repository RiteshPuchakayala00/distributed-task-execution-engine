package com.engine.master.registry;

/**
 * Enum representing the lifecycle states of a worker node in the cluster.
 *
 * <p>Workers transition through these states based on heartbeat signals,
 * task assignments, and administrative actions.</p>
 *
 * @author Engine Team
 */
public enum WorkerStatus {

    /**
     * Worker is registered and has no tasks assigned.
     */
    IDLE,

    /**
     * Worker has active tasks but can accept more.
     */
    BUSY,

    /**
     * Worker is at maximum capacity and cannot accept new tasks.
     */
    OVERLOADED,

    /**
     * Worker has missed heartbeats; no new tasks should be assigned.
     */
    UNRESPONSIVE,

    /**
     * Worker has been declared dead and is pending cleanup.
     */
    DEAD;

    /**
     * Determines whether a worker in this state can accept new tasks.
     *
     * @return {@code true} if the state is {@link #IDLE} or {@link #BUSY}, {@code false} otherwise
     */
    public boolean isAvailable() {
        return this == IDLE || this == BUSY;
    }

    /**
     * Determines whether a worker in this state is considered healthy.
     *
     * @return {@code true} if the state is {@link #IDLE}, {@link #BUSY}, or {@link #OVERLOADED},
     *         {@code false} otherwise
     */
    public boolean isHealthy() {
        return this == IDLE || this == BUSY || this == OVERLOADED;
    }
}
