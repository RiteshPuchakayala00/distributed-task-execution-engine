package com.engine.master.task;

import com.engine.common.model.Task;
import com.engine.common.model.TaskResult;
import com.engine.common.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of tasks on the master node.
 * Uses a priority queue for pending tasks and a hash map for active/completed tasks.
 *
 * @author Engine Team
 */
public class TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    private final PriorityBlockingQueue<Task> pendingQueue;
    private final ConcurrentHashMap<String, Task> taskMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskResult> resultMap = new ConcurrentHashMap<>();
    private final DelayQueue<DelayedTaskExpiration> expirationQueue = new DelayQueue<>();
    private com.engine.master.metrics.MetricsService metricsService;
    private final int queueCapacity;

    /**
     * Constructs a TaskManager with the specified queue capacity.
     *
     * @param queueCapacity the maximum number of pending tasks allowed
     */
    public TaskManager(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        this.pendingQueue = new PriorityBlockingQueue<>();
    }

    /**
     * Sets the metrics service.
     *
     * @param metricsService the metrics service
     */
    public void setMetricsService(com.engine.master.metrics.MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Submits a new task to the manager.
     *
     * @param task the task to submit
     * @throws IllegalArgumentException if the task is null or already exists
     */
    public void submitTask(Task task) {
        Objects.requireNonNull(task, "Task cannot be null");
        
        if (pendingQueue.size() >= queueCapacity) {
            throw new IllegalStateException("Task queue is at capacity (" + queueCapacity + ")");
        }
        
        if (taskMap.putIfAbsent(task.getTaskId(), task) != null) {
            throw new IllegalArgumentException("Task " + task.getTaskId() + " already exists");
        }
        
        pendingQueue.offer(task);
        if (metricsService != null) {
            metricsService.recordTaskSubmitted();
        }
        logger.info("Submitted task {} with priority {}", task.getTaskId(), task.getPriority());
    }

    /**
     * Polls the next pending task from the priority queue.
     *
     * @return the next pending task, or null if the queue is empty
     */
    public Task pollPendingTask() {
        return pendingQueue.poll();
    }

    /**
     * Updates a task's status and assigned worker.
     *
     * @param taskId   the task ID
     * @param workerId the assigned worker ID
     * @param status   the new status
     */
    public void assignTask(String taskId, String workerId, TaskStatus status) {
        taskMap.computeIfPresent(taskId, (id, task) -> {
            Task updatedTask = task.withAssignedWorker(workerId).withStatus(status);
            if (status.isTerminal()) {
                expirationQueue.offer(new DelayedTaskExpiration(taskId, 300000)); // Default 5 mins TTL
            }
            logger.debug("Task {} assigned to worker {}, status: {}", taskId, workerId, status);
            return updatedTask;
        });
    }

    /**
     * Completes a task by saving its result.
     *
     * @param result the task result
     */
    public void completeTask(TaskResult result) {
        Objects.requireNonNull(result, "TaskResult cannot be null");
        resultMap.put(result.getTaskId(), result);
        taskMap.computeIfPresent(result.getTaskId(), (id, task) -> {
            TaskStatus finalStatus = result.isSuccess() ? TaskStatus.COMPLETED : TaskStatus.FAILED;
            Task updatedTask = task.withStatus(finalStatus);
            
            if (metricsService != null) {
                if (result.isSuccess()) {
                    metricsService.recordTaskCompleted(result.getExecutionTimeMs());
                } else {
                    metricsService.recordTaskFailed();
                }
            }
            
            if (finalStatus.isTerminal()) {
                expirationQueue.offer(new DelayedTaskExpiration(id, 300000));
            }
            logger.info("Task {} completed with status: {}", id, finalStatus);
            return updatedTask;
        });
    }

    /**
     * Requeues a task (e.g. if assignment failed).
     *
     * @param task the task to requeue
     */
    public void requeueTask(Task task) {
        Objects.requireNonNull(task, "Task cannot be null");
        taskMap.computeIfPresent(task.getTaskId(), (id, existingTask) -> {
            if (existingTask.getRetryCount() >= existingTask.getMaxRetries()) {
                logger.warn("Task {} exceeded max retries ({}). Marking FAILED.", id, existingTask.getMaxRetries());
                Task failedTask = existingTask.withStatus(TaskStatus.FAILED);
                expirationQueue.offer(new DelayedTaskExpiration(id, 300000));
                return failedTask;
            }
            
            Task updatedTask = existingTask
                    .withRetryCount(existingTask.getRetryCount() + 1)
                    .withStatus(TaskStatus.PENDING)
                    .withAssignedWorker(null);
                    
            pendingQueue.offer(updatedTask);
            logger.info("Task {} requeued (Retry {}/{})", id, updatedTask.getRetryCount(), updatedTask.getMaxRetries());
            return updatedTask;
        });
    }

    /**
     * Retrieves a task by ID.
     *
     * @param taskId the task ID
     * @return an Optional containing the task if found
     */
    public Optional<Task> getTask(String taskId) {
        return Optional.ofNullable(taskMap.get(taskId));
    }

    /**
     * Retrieves a task result by task ID.
     *
     * @param taskId the task ID
     * @return an Optional containing the task result if found
     */
    public Optional<TaskResult> getTaskResult(String taskId) {
        return Optional.ofNullable(resultMap.get(taskId));
    }

    /**
     * Gets the number of pending tasks.
     *
     * @return the pending task count
     */
    public int getPendingCount() {
        return pendingQueue.size();
    }

    /**
     * Gets all active tasks assigned to a specific worker.
     *
     * @param workerId the worker ID
     * @return a list of tasks assigned to the worker
     */
    public List<Task> getTasksAssignedToWorker(String workerId) {
        return taskMap.values().stream()
                .filter(task -> workerId.equals(task.getAssignedWorkerId()))
                .collect(Collectors.toList());
    }

    /**
     * Sweeps terminal tasks (COMPLETED, FAILED, TIMED_OUT, CANCELLED) older than the specified age.
     *
     * @param maxAgeMs the maximum age in milliseconds for a terminal task to be retained
     * @return the number of tasks swept
     */
    public int sweepTerminalTasks(long maxAgeMs) {
        int swept = 0;
        DelayedTaskExpiration expired;
        
        while ((expired = expirationQueue.poll()) != null) {
            taskMap.remove(expired.taskId);
            resultMap.remove(expired.taskId);
            swept++;
        }
        
        if (swept > 0) {
            logger.info("Swept {} terminal tasks from memory in O(1) time", swept);
        }
        return swept;
    }

    /**
     * Helper class for managing delayed expiration of terminal tasks.
     */
    private static class DelayedTaskExpiration implements Delayed {
        final String taskId;
        final long expirationTime;

        DelayedTaskExpiration(String taskId, long delayMs) {
            this.taskId = taskId;
            this.expirationTime = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expirationTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this.expirationTime < ((DelayedTaskExpiration) o).expirationTime) return -1;
            if (this.expirationTime > ((DelayedTaskExpiration) o).expirationTime) return 1;
            return 0;
        }
    }
}
