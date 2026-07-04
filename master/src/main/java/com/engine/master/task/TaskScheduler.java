package com.engine.master.task;

import com.engine.common.model.Task;
import com.engine.common.model.TaskStatus;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import com.engine.common.serialization.JavaSerializer;
import com.engine.common.serialization.Serializer;
import com.engine.master.network.ConnectionManager;
import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.registry.WorkerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Background thread that assigns pending tasks to available workers.
 *
 * @author Engine Team
 */
public class TaskScheduler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

    private final TaskManager taskManager;
    private final WorkerRegistry workerRegistry;
    private final ConnectionManager connectionManager;
    private final Serializer serializer;

    /**
     * Constructs a new TaskScheduler.
     *
     * @param taskManager       the task manager
     * @param workerRegistry    the worker registry
     * @param connectionManager the connection manager
     */
    public TaskScheduler(TaskManager taskManager, WorkerRegistry workerRegistry, ConnectionManager connectionManager) {
        this.taskManager = Objects.requireNonNull(taskManager, "TaskManager cannot be null");
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "WorkerRegistry cannot be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "ConnectionManager cannot be null");
        this.serializer = new JavaSerializer();
    }

    @Override
    public void run() {
        try {
            while (taskManager.getPendingCount() > 0) {
                // Find all workers that are available and have capacity
                List<WorkerInfo> availableWorkers = workerRegistry.getAvailableWorkers().stream()
                        .filter(w -> w.getCurrentLoad() < w.getAvailableCores())
                        .sorted(java.util.Comparator.comparingDouble(WorkerInfo::getLoadFactor))
                        .toList();

                if (availableWorkers.isEmpty()) {
                    logger.trace("No workers with available capacity. Pausing scheduling.");
                    break;
                }

                // Get a pending task
                Task task = taskManager.pollPendingTask();
                if (task == null) {
                    break; // Queue is empty
                }

                // Pick the worker with the lowest load factor
                WorkerInfo targetWorker = availableWorkers.get(0);
                String workerId = targetWorker.getWorkerId();

                try {
                    // Update states atomically
                    targetWorker.incrementLoad();
                    if (targetWorker.getCurrentLoad() >= targetWorker.getAvailableCores()) {
                        workerRegistry.compareAndSetStatus(workerId, WorkerStatus.IDLE, WorkerStatus.BUSY);
                    }
                    taskManager.assignTask(task.getTaskId(), workerId, TaskStatus.ASSIGNED);

                    // Send assignment message
                    Message msg = new Message.Builder(MessageType.TASK_ASSIGN)
                            .payload(serializer.serialize(task))
                            .build();

                    connectionManager.sendToWorker(workerId, msg);
                    logger.info("Assigned task {} to worker {} (Load: {}/{})", 
                            task.getTaskId(), workerId, targetWorker.getCurrentLoad(), targetWorker.getAvailableCores());

                } catch (IOException e) {
                    logger.error("Failed to send task {} to worker {}", task.getTaskId(), workerId, e);
                    // Requeue task, decrement load, and mark worker as dead
                    targetWorker.decrementLoad();
                    workerRegistry.updateStatus(workerId, WorkerStatus.DEAD);
                    taskManager.requeueTask(task);
                } catch (Exception e) {
                    logger.error("Unexpected error during task scheduling", e);
                    targetWorker.decrementLoad();
                    taskManager.requeueTask(task);
                }
            }
        } catch (Exception e) {
            logger.error("Error in scheduling loop", e);
        }
    }
}
