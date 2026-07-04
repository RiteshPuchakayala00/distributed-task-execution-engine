package com.engine.master.registry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable, thread-safe, server-side metadata for a registered worker node.
 *
 * <p>This class is <strong>not</strong> sent over the wire — it is the master's
 * internal view of a worker's identity and current state. Identity fields are
 * immutable; runtime fields ({@code status}, {@code lastHeartbeatMs},
 * {@code currentLoad}) are updated concurrently from handler threads.</p>
 *
 * @author Engine Team
 */
public class WorkerInfo {

    // ── Immutable identity fields ────────────────────────────────────────

    private final String workerId;
    private final String hostname;
    private final int port;
    private final int availableCores;
    private final long registeredAt;

    // ── Mutable thread-safe fields ───────────────────────────────────────

    private final AtomicReference<WorkerStatus> status;
    private volatile long lastHeartbeatMs;
    private final AtomicInteger currentLoad;

    /**
     * Creates a new {@code WorkerInfo} with the given identity fields.
     *
     * @param workerId       unique identifier for the worker (must not be {@code null})
     * @param hostname       hostname or IP address of the worker (must not be {@code null})
     * @param port           port number the worker is listening on
     * @param availableCores number of cores available on the worker (must be &gt; 0)
     * @throws NullPointerException     if {@code workerId} or {@code hostname} is {@code null}
     * @throws IllegalArgumentException if {@code availableCores} is not positive
     */
    public WorkerInfo(String workerId, String hostname, int port, int availableCores) {
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.hostname = Objects.requireNonNull(hostname, "hostname must not be null");
        if (availableCores <= 0) {
            throw new IllegalArgumentException("availableCores must be > 0, got " + availableCores);
        }
        this.port = port;
        this.availableCores = availableCores;
        this.registeredAt = System.currentTimeMillis();
        this.status = new AtomicReference<>(WorkerStatus.IDLE);
        this.lastHeartbeatMs = System.currentTimeMillis();
        this.currentLoad = new AtomicInteger(0);
    }

    // ── Getters ──────────────────────────────────────────────────────────

    /**
     * Returns the unique identifier for this worker.
     *
     * @return the worker ID
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Returns the hostname or IP address of this worker.
     *
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Returns the port number the worker is listening on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the number of cores available on this worker.
     *
     * @return the available core count
     */
    public int getAvailableCores() {
        return availableCores;
    }

    /**
     * Returns the epoch-millisecond timestamp when this worker was registered.
     *
     * @return registration timestamp in epoch milliseconds
     */
    public long getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Returns the current status of this worker.
     *
     * @return the current {@link WorkerStatus}
     */
    public WorkerStatus getStatus() {
        return status.get();
    }

    /**
     * Returns the epoch-millisecond timestamp of the last heartbeat received.
     *
     * @return last heartbeat timestamp in epoch milliseconds
     */
    public long getLastHeartbeatMs() {
        return lastHeartbeatMs;
    }

    /**
     * Returns the current task load on this worker.
     *
     * @return the current load (number of assigned tasks)
     */
    public int getCurrentLoad() {
        return currentLoad.get();
    }

    // ── Mutators ─────────────────────────────────────────────────────────

    /**
     * Sets the worker's status to the given value.
     *
     * @param status the new status (must not be {@code null})
     * @throws NullPointerException if {@code status} is {@code null}
     */
    public void setStatus(WorkerStatus status) {
        this.status.set(Objects.requireNonNull(status, "status must not be null"));
    }

    /**
     * Atomically sets the worker's status to the given updated value
     * if the current value == the expected value.
     *
     * @param expect the expected status
     * @param update the new status
     * @return true if successful
     */
    public boolean compareAndSetStatus(WorkerStatus expect, WorkerStatus update) {
        return this.status.compareAndSet(expect, update);
    }

    /**
     * Updates the last heartbeat timestamp to the current time.
     */
    public void updateLastHeartbeat() {
        this.lastHeartbeatMs = System.currentTimeMillis();
    }

    /**
     * Atomically increments the current load by one.
     *
     * @return the new load value after incrementing
     */
    public int incrementLoad() {
        return currentLoad.incrementAndGet();
    }

    /**
     * Atomically decrements the current load by one, with a minimum of zero.
     *
     * @return the new load value after decrementing
     */
    public int decrementLoad() {
        return currentLoad.updateAndGet(v -> Math.max(0, v - 1));
    }

    /**
     * Returns the load factor as the ratio of current load to available cores.
     *
     * @return the load factor; {@code 0.0} if {@code availableCores} is zero
     */
    public double getLoadFactor() {
        if (availableCores == 0) {
            return 0.0;
        }
        return (double) currentLoad.get() / availableCores;
    }

    /**
     * Determines whether this worker can accept new tasks based on its status.
     *
     * @return {@code true} if the worker's status is available
     */
    public boolean isAvailable() {
        return status.get().isAvailable();
    }

    // ── Object overrides ─────────────────────────────────────────────────

    /**
     * Two {@code WorkerInfo} instances are equal if they share the same {@code workerId}.
     *
     * @param o the reference object with which to compare
     * @return {@code true} if the given object has the same worker ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkerInfo that = (WorkerInfo) o;
        return Objects.equals(workerId, that.workerId);
    }

    /**
     * Returns a hash code based on the {@code workerId}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(workerId);
    }

    /**
     * Returns a human-readable string representation of this worker's state.
     *
     * @return a summary string including worker ID, hostname, status, and load
     */
    @Override
    public String toString() {
        return "WorkerInfo{" +
                "workerId='" + workerId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", status=" + status.get() +
                ", load=" + currentLoad.get() + "/" + availableCores +
                '}';
    }
}
