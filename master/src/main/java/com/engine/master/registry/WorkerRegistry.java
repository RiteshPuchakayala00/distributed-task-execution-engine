package com.engine.master.registry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central, thread-safe store for worker metadata.
 *
 * <p>All mutations are safe for concurrent access from multiple handler threads
 * thanks to the underlying {@link ConcurrentHashMap}.</p>
 *
 * @author Engine Team
 */
public class WorkerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRegistry.class);

    private final ConcurrentHashMap<String, WorkerInfo> workers = new ConcurrentHashMap<>();

    /**
     * Registers a worker in the registry.
     *
     * <p>If a worker with the same ID already exists it is replaced and a
     * warning is logged.</p>
     *
     * @param worker the worker metadata to register (must not be {@code null})
     * @throws NullPointerException if {@code worker} is {@code null}
     */
    public void registerWorker(WorkerInfo worker) {
        Objects.requireNonNull(worker, "worker must not be null");
        WorkerInfo previous = workers.put(worker.getWorkerId(), worker);
        if (previous != null) {
            logger.warn("Replaced existing worker registration for workerId={}", worker.getWorkerId());
        }
        logger.info("Registered worker: workerId={}, hostname={}, cores={}",
                worker.getWorkerId(), worker.getHostname(), worker.getAvailableCores());
    }

    /**
     * Removes a worker from the registry.
     *
     * @param workerId the ID of the worker to deregister
     * @return {@code true} if the worker existed and was removed, {@code false} otherwise
     */
    public boolean deregisterWorker(String workerId) {
        WorkerInfo removed = workers.remove(workerId);
        if (removed != null) {
            logger.info("Deregistered worker: workerId={}", workerId);
            return true;
        }
        return false;
    }

    /**
     * Retrieves worker metadata by ID.
     *
     * @param workerId the worker ID to look up
     * @return an {@link Optional} containing the {@link WorkerInfo} if found, or empty
     */
    public Optional<WorkerInfo> getWorker(String workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    /**
     * Updates the status of a specific worker.
     *
     * @param workerId the worker ID
     * @param status   the new status to set
     */
    public void updateStatus(String workerId, WorkerStatus status) {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            // For forced updates like DEAD from heartbeat, just set it.
            worker.setStatus(status);
            logger.debug("Forced status for worker {}: {}", workerId, status);
        }
    }

    /**
     * Atomically updates the status of a specific worker if it matches the expected status.
     *
     * @param workerId the worker ID
     * @param expect   the expected current status
     * @param update   the new status to set
     * @return true if successful
     */
    public boolean compareAndSetStatus(String workerId, WorkerStatus expect, WorkerStatus update) {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            boolean success = worker.compareAndSetStatus(expect, update);
            if (success) {
                logger.debug("CAS status for worker {}: {} -> {}", workerId, expect, update);
            }
            return success;
        }
        return false;
    }

    /**
     * Records a heartbeat for a specific worker by updating its last-heartbeat
     * timestamp.
     *
     * @param workerId the worker ID
     */
    public void updateHeartbeat(String workerId) {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            worker.updateLastHeartbeat();
            logger.trace("Updated heartbeat for worker {}", workerId);
        }
    }

    /**
     * Returns a list of workers whose status indicates availability.
     *
     * @return an unmodifiable list of available {@link WorkerInfo} instances
     */
    public List<WorkerInfo> getAvailableWorkers() {
        return workers.values().stream()
                .filter(WorkerInfo::isAvailable)
                .toList();
    }

    /**
     * Returns a list of workers with the specified status.
     *
     * @param status the status to filter by
     * @return an unmodifiable list of matching {@link WorkerInfo} instances
     */
    public List<WorkerInfo> getWorkersByStatus(WorkerStatus status) {
        return workers.values().stream()
                .filter(w -> w.getStatus() == status)
                .toList();
    }

    /**
     * Returns a collection view of all registered workers.
     *
     * @return a view of all {@link WorkerInfo} instances
     */
    public java.util.Collection<WorkerInfo> getAllWorkers() {
        return workers.values();
    }

    /**
     * Returns the total number of registered workers.
     *
     * @return the worker count
     */
    public int getWorkerCount() {
        return workers.size();
    }

    /**
     * Checks whether a worker with the given ID is registered.
     *
     * @param workerId the worker ID to check
     * @return {@code true} if the worker exists in the registry
     */
    public boolean hasWorker(String workerId) {
        return workers.containsKey(workerId);
    }
}
