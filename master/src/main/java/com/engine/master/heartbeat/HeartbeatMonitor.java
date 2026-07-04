package com.engine.master.heartbeat;

import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.registry.WorkerStatus;
import com.engine.master.recovery.FailureRecoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Monitors worker heartbeats and marks unresponsive/dead workers.
 *
 * @author Engine Team
 */
public class HeartbeatMonitor implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final WorkerRegistry workerRegistry;
    private final FailureRecoveryManager recoveryManager;
    private final long unresponsiveThresholdMs;
    private final long deadThresholdMs;

    /**
     * Constructs a HeartbeatMonitor.
     *
     * @param workerRegistry          the worker registry
     * @param recoveryManager         the failure recovery manager
     * @param unresponsiveThresholdMs threshold in ms to mark worker UNRESPONSIVE
     * @param deadThresholdMs         threshold in ms to mark worker DEAD
     */
    public HeartbeatMonitor(WorkerRegistry workerRegistry, 
                            FailureRecoveryManager recoveryManager,
                            long unresponsiveThresholdMs, 
                            long deadThresholdMs) {
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry must not be null");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager must not be null");
        this.unresponsiveThresholdMs = unresponsiveThresholdMs;
        this.deadThresholdMs = deadThresholdMs;
    }

    @Override
    public void run() {
        try {
            long now = System.currentTimeMillis();
            Collection<WorkerInfo> workers = workerRegistry.getAllWorkers();
            
            for (WorkerInfo worker : workers) {
                if (worker.getStatus() == WorkerStatus.DEAD) {
                    continue; // Already processed
                }
                
                long timeSinceLastHeartbeat = now - worker.getLastHeartbeatMs();
                
                if (timeSinceLastHeartbeat > deadThresholdMs) {
                    logger.error("Worker {} has missed heartbeats for {}ms. Marking DEAD.", 
                            worker.getWorkerId(), timeSinceLastHeartbeat);
                    workerRegistry.updateStatus(worker.getWorkerId(), WorkerStatus.DEAD);
                    recoveryManager.handleWorkerFailure(worker.getWorkerId());
                } else if (timeSinceLastHeartbeat > unresponsiveThresholdMs && worker.getStatus() != WorkerStatus.UNRESPONSIVE) {
                    logger.warn("Worker {} has not sent a heartbeat in {}ms. Marking UNRESPONSIVE.", 
                            worker.getWorkerId(), timeSinceLastHeartbeat);
                    workerRegistry.updateStatus(worker.getWorkerId(), WorkerStatus.UNRESPONSIVE);
                } else if (timeSinceLastHeartbeat <= unresponsiveThresholdMs && worker.getStatus() == WorkerStatus.UNRESPONSIVE) {
                    // Worker recovered
                    logger.info("Worker {} recovered and is sending heartbeats again. Marking IDLE.", worker.getWorkerId());
                    workerRegistry.updateStatus(worker.getWorkerId(), WorkerStatus.IDLE);
                }
            }
        } catch (Exception e) {
            logger.error("Error in HeartbeatMonitor loop", e);
        }
    }
}
