package com.engine.master.recovery;

import com.engine.common.model.Task;
import com.engine.master.network.ConnectionManager;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Handles recovery of tasks when a worker fails.
 *
 * @author Engine Team
 */
public class FailureRecoveryManager {

    private static final Logger logger = LoggerFactory.getLogger(FailureRecoveryManager.class);

    private final TaskManager taskManager;
    private final WorkerRegistry workerRegistry;
    private final ConnectionManager connectionManager;

    /**
     * Constructs a FailureRecoveryManager.
     *
     * @param taskManager       the task manager
     * @param workerRegistry    the worker registry
     * @param connectionManager the connection manager
     */
    public FailureRecoveryManager(TaskManager taskManager, WorkerRegistry workerRegistry, ConnectionManager connectionManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager must not be null");
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry must not be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    }

    /**
     * Handles the failure of a worker by re-queuing its assigned tasks and deregistering it.
     *
     * @param workerId the ID of the failed worker
     */
    public void handleWorkerFailure(String workerId) {
        logger.info("Initiating failure recovery for worker {}", workerId);

        // 1. Get all tasks assigned to this worker
        List<Task> orphanedTasks = taskManager.getTasksAssignedToWorker(workerId);
        logger.info("Found {} orphaned tasks for worker {}", orphanedTasks.size(), workerId);

        // 2. Re-queue tasks
        for (Task task : orphanedTasks) {
            logger.info("Re-queuing orphaned task {} (retry count: {})", task.getTaskId(), task.getRetryCount());
            taskManager.requeueTask(task);
        }

        // 3. Drop connection and deregister
        connectionManager.removeConnection(workerId);
        workerRegistry.deregisterWorker(workerId);
        
        logger.info("Failure recovery completed for worker {}", workerId);
    }
}
