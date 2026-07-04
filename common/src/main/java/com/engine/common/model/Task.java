package com.engine.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a unit of work within the distributed task execution engine.
 *
 * <p>A {@code Task} carries a typed payload, a priority level, retry configuration,
 * and lifecycle metadata. Tasks are compared first by priority (descending — higher
 * priority values come first) and then by creation time (ascending — earlier tasks
 * come first), making them suitable for use in priority-based scheduling queues.</p>
 *
 * <p>Instances are created exclusively through the {@link Builder} and are fully
 * immutable. State transitions are performed via copy methods
 * ({@link #withStatus}, {@link #withRetryCount}, {@link #withAssignedWorker}) that
 * return new {@code Task} instances.</p>
 *
 * @author Engine Team
 */
public final class Task implements Serializable, Comparable<Task> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final String taskType;
    private final byte[] payload;
    private final TaskPriority priority;
    private final TaskStatus status;
    private final int maxRetries;
    private final int retryCount;
    private final long timeoutMs;
    private final long createdAt;
    private final String assignedWorkerId;

    /**
     * Private constructor — use {@link Builder} to create instances.
     *
     * @param builder the builder containing the field values
     */
    private Task(Builder builder) {
        this.taskId = builder.taskId;
        this.taskType = builder.taskType;
        this.payload = builder.payload != null ? Arrays.copyOf(builder.payload, builder.payload.length) : null;
        this.priority = builder.priority;
        this.status = builder.status;
        this.maxRetries = builder.maxRetries;
        this.retryCount = builder.retryCount;
        this.timeoutMs = builder.timeoutMs;
        this.createdAt = builder.createdAt;
        this.assignedWorkerId = builder.assignedWorkerId;
    }

    /**
     * Returns the unique identifier of this task.
     *
     * @return the task ID (UUID string)
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Returns the logical type of this task, used for routing to appropriate handlers.
     *
     * @return the task type
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * Returns a defensive copy of the task payload.
     *
     * @return a copy of the payload byte array, or {@code null} if no payload was set
     */
    public byte[] getPayload() {
        return payload != null ? Arrays.copyOf(payload, payload.length) : null;
    }

    /**
     * Returns the priority level of this task.
     *
     * @return the task priority
     */
    public TaskPriority getPriority() {
        return priority;
    }

    /**
     * Returns the current lifecycle status of this task.
     *
     * @return the task status
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Returns the maximum number of retry attempts allowed for this task.
     *
     * @return the max retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the number of retries that have been attempted so far.
     *
     * @return the current retry count
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Returns the execution timeout in milliseconds.
     *
     * @return the timeout in milliseconds
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Returns the epoch-millisecond timestamp when this task was created.
     *
     * @return the creation timestamp
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the identifier of the worker to which this task is assigned,
     * or {@code null} if not yet assigned.
     *
     * @return the assigned worker ID, or {@code null}
     */
    public String getAssignedWorkerId() {
        return assignedWorkerId;
    }

    /**
     * Returns a new {@code Task} with the specified status, leaving all other fields unchanged.
     *
     * @param newStatus the new status
     * @return a new {@code Task} instance with the updated status
     */
    public Task withStatus(TaskStatus newStatus) {
        return new Builder(this).status(newStatus).build();
    }

    /**
     * Returns a new {@code Task} with the specified retry count, leaving all other fields unchanged.
     *
     * @param newRetryCount the new retry count
     * @return a new {@code Task} instance with the updated retry count
     */
    public Task withRetryCount(int newRetryCount) {
        return new Builder(this).retryCount(newRetryCount).build();
    }

    /**
     * Returns a new {@code Task} with the specified assigned worker, leaving all other fields unchanged.
     *
     * @param workerId the worker identifier to assign
     * @return a new {@code Task} instance with the updated assigned worker
     */
    public Task withAssignedWorker(String workerId) {
        return new Builder(this).assignedWorkerId(workerId).build();
    }

    /**
     * Compares this task with another for ordering.
     *
     * <p>Tasks are ordered by priority descending (higher priority first),
     * then by creation time ascending (earlier tasks first).</p>
     *
     * @param other the other task to compare against
     * @return a negative integer, zero, or positive integer as this task should
     *         be scheduled before, at the same time as, or after the other task
     */
    @Override
    public int compareTo(Task other) {
        // Higher priority value = higher urgency → should come first (descending)
        int priorityComparison = Integer.compare(other.priority.getLevel(), this.priority.getLevel());
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        // Earlier creation time = should come first (ascending)
        return Long.compare(this.createdAt, other.createdAt);
    }

    /**
     * Two tasks are considered equal if they share the same {@code taskId}.
     *
     * @param o the reference object with which to compare
     * @return {@code true} if the other object is a {@code Task} with the same task ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Task task = (Task) o;
        return Objects.equals(taskId, task.taskId);
    }

    /**
     * Returns a hash code based solely on the {@code taskId}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    /**
     * Returns a concise string representation including task ID, type, priority, and status.
     *
     * @return a human-readable string
     */
    @Override
    public String toString() {
        return "Task{" +
                "taskId='" + taskId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", priority=" + priority +
                ", status=" + status +
                '}';
    }

    /**
     * Builder for constructing {@link Task} instances.
     *
     * <p>The no-argument constructor generates sensible defaults:
     * a random UUID for the task ID, {@link TaskStatus#PENDING} status,
     * {@link TaskPriority#MEDIUM} priority, 3 max retries, 0 retry count,
     * 60 000 ms timeout, and the current epoch time for creation timestamp.</p>
     *
     * @author Engine Team
     */
    public static final class Builder {

        private String taskId;
        private String taskType;
        private byte[] payload;
        private TaskPriority priority;
        private TaskStatus status;
        private int maxRetries;
        private int retryCount;
        private long timeoutMs;
        private long createdAt;
        private String assignedWorkerId;

        /**
         * Creates a new builder with default values.
         */
        public Builder() {
            this.taskId = UUID.randomUUID().toString();
            this.createdAt = System.currentTimeMillis();
            this.status = TaskStatus.PENDING;
            this.priority = TaskPriority.MEDIUM;
            this.maxRetries = 3;
            this.retryCount = 0;
            this.timeoutMs = 60_000L;
        }

        /**
         * Creates a new builder pre-populated with all fields from an existing task.
         *
         * @param task the task to copy values from
         */
        public Builder(Task task) {
            this.taskId = task.taskId;
            this.taskType = task.taskType;
            this.payload = task.payload != null ? Arrays.copyOf(task.payload, task.payload.length) : null;
            this.priority = task.priority;
            this.status = task.status;
            this.maxRetries = task.maxRetries;
            this.retryCount = task.retryCount;
            this.timeoutMs = task.timeoutMs;
            this.createdAt = task.createdAt;
            this.assignedWorkerId = task.assignedWorkerId;
        }

        /**
         * Sets the task ID.
         *
         * @param taskId the task ID
         * @return this builder
         */
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Sets the task type.
         *
         * @param taskType the logical task type
         * @return this builder
         */
        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        /**
         * Sets the task payload (defensive copy is made).
         *
         * @param payload the payload byte array
         * @return this builder
         */
        public Builder payload(byte[] payload) {
            this.payload = payload != null ? Arrays.copyOf(payload, payload.length) : null;
            return this;
        }

        /**
         * Sets the task priority.
         *
         * @param priority the priority level
         * @return this builder
         */
        public Builder priority(TaskPriority priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the task status.
         *
         * @param status the lifecycle status
         * @return this builder
         */
        public Builder status(TaskStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxRetries the max retry count (must be ≥ 0)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the current retry count.
         *
         * @param retryCount the number of retries attempted so far
         * @return this builder
         */
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        /**
         * Sets the execution timeout in milliseconds.
         *
         * @param timeoutMs the timeout in milliseconds (must be &gt; 0)
         * @return this builder
         */
        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the creation timestamp (epoch milliseconds).
         *
         * @param createdAt the creation timestamp
         * @return this builder
         */
        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the assigned worker ID.
         *
         * @param assignedWorkerId the worker identifier, or {@code null}
         * @return this builder
         */
        public Builder assignedWorkerId(String assignedWorkerId) {
            this.assignedWorkerId = assignedWorkerId;
            return this;
        }

        /**
         * Builds and returns an immutable {@link Task} instance.
         *
         * @return the constructed task
         * @throws IllegalStateException if any validation constraint is violated
         */
        public Task build() {
            if (taskType == null || taskType.isBlank()) {
                throw new IllegalStateException("taskType must not be null or blank");
            }
            if (priority == null) {
                throw new IllegalStateException("priority must not be null");
            }
            if (status == null) {
                throw new IllegalStateException("status must not be null");
            }
            if (maxRetries < 0) {
                throw new IllegalStateException("maxRetries must be >= 0, got: " + maxRetries);
            }
            if (timeoutMs <= 0) {
                throw new IllegalStateException("timeoutMs must be > 0, got: " + timeoutMs);
            }
            return new Task(this);
        }
    }
}
