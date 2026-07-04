package com.engine.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable representation of the result produced by a worker after executing a {@link Task}.
 *
 * <p>A {@code TaskResult} captures whether execution succeeded or failed, the optional
 * result payload or error message, execution duration, and completion timestamp.
 * Instances are created via the {@link Builder} or the convenience factory methods
 * {@link #success} and {@link #failure}.</p>
 *
 * @author Engine Team
 */
public final class TaskResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final String workerId;
    private final boolean success;
    private final byte[] result;
    private final String errorMessage;
    private final long executionTimeMs;
    private final long completedAt;

    /**
     * Private constructor — use {@link Builder} or factory methods to create instances.
     *
     * @param builder the builder containing the field values
     */
    private TaskResult(Builder builder) {
        this.taskId = builder.taskId;
        this.workerId = builder.workerId;
        this.success = builder.success;
        this.result = builder.result != null ? Arrays.copyOf(builder.result, builder.result.length) : null;
        this.errorMessage = builder.errorMessage;
        this.executionTimeMs = builder.executionTimeMs;
        this.completedAt = builder.completedAt;
    }

    /**
     * Returns the identifier of the task that produced this result.
     *
     * @return the task ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Returns the identifier of the worker that executed the task.
     *
     * @return the worker ID
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * Returns whether the task execution was successful.
     *
     * @return {@code true} if the task completed successfully, {@code false} otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns a defensive copy of the result payload, or {@code null} if no result was produced.
     *
     * @return a copy of the result byte array, or {@code null}
     */
    public byte[] getResult() {
        return result != null ? Arrays.copyOf(result, result.length) : null;
    }

    /**
     * Returns the error message if the task failed, or {@code null} on success.
     *
     * @return the error message, or {@code null}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the wall-clock execution time of the task in milliseconds.
     *
     * @return the execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Returns the epoch-millisecond timestamp when the task execution completed.
     *
     * @return the completion timestamp
     */
    public long getCompletedAt() {
        return completedAt;
    }

    /**
     * Creates a successful {@code TaskResult}.
     *
     * @param taskId          the identifier of the completed task
     * @param workerId        the identifier of the worker that executed the task
     * @param result          the result payload (may be {@code null})
     * @param executionTimeMs the execution duration in milliseconds
     * @return a new successful {@code TaskResult}
     */
    public static TaskResult success(String taskId, String workerId, byte[] result, long executionTimeMs) {
        return new Builder()
                .taskId(taskId)
                .workerId(workerId)
                .success(true)
                .result(result)
                .executionTimeMs(executionTimeMs)
                .completedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * Creates a failed {@code TaskResult}.
     *
     * @param taskId          the identifier of the failed task
     * @param workerId        the identifier of the worker that attempted the task
     * @param errorMessage    a description of the failure
     * @param executionTimeMs the execution duration in milliseconds
     * @return a new failed {@code TaskResult}
     */
    public static TaskResult failure(String taskId, String workerId, String errorMessage, long executionTimeMs) {
        return new Builder()
                .taskId(taskId)
                .workerId(workerId)
                .success(false)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .completedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * Two task results are considered equal if they share the same {@code taskId}.
     *
     * @param o the reference object with which to compare
     * @return {@code true} if the other object is a {@code TaskResult} with the same task ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskResult that = (TaskResult) o;
        return Objects.equals(taskId, that.taskId);
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
     * Returns a concise string representation of this task result.
     *
     * @return a human-readable string
     */
    @Override
    public String toString() {
        return "TaskResult{" +
                "taskId='" + taskId + '\'' +
                ", workerId='" + workerId + '\'' +
                ", success=" + success +
                ", executionTimeMs=" + executionTimeMs +
                ", completedAt=" + completedAt +
                (errorMessage != null ? ", errorMessage='" + errorMessage + '\'' : "") +
                '}';
    }

    /**
     * Builder for constructing {@link TaskResult} instances.
     *
     * <p>At minimum, {@code taskId} and {@code workerId} must be set before calling
     * {@link #build()}.</p>
     *
     * @author Engine Team
     */
    public static final class Builder {

        private String taskId;
        private String workerId;
        private boolean success;
        private byte[] result;
        private String errorMessage;
        private long executionTimeMs;
        private long completedAt;

        /**
         * Creates a new builder with no defaults.
         */
        public Builder() {
            // All fields start at their Java defaults
        }

        /**
         * Sets the task ID.
         *
         * @param taskId the task identifier
         * @return this builder
         */
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Sets the worker ID.
         *
         * @param workerId the worker identifier
         * @return this builder
         */
        public Builder workerId(String workerId) {
            this.workerId = workerId;
            return this;
        }

        /**
         * Sets whether the task succeeded.
         *
         * @param success {@code true} for success, {@code false} for failure
         * @return this builder
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the result payload (defensive copy is made).
         *
         * @param result the result byte array, or {@code null}
         * @return this builder
         */
        public Builder result(byte[] result) {
            this.result = result != null ? Arrays.copyOf(result, result.length) : null;
            return this;
        }

        /**
         * Sets the error message.
         *
         * @param errorMessage the error description, or {@code null}
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Sets the execution time in milliseconds.
         *
         * @param executionTimeMs the execution duration
         * @return this builder
         */
        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        /**
         * Sets the completion timestamp (epoch milliseconds).
         *
         * @param completedAt the completion timestamp
         * @return this builder
         */
        public Builder completedAt(long completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        /**
         * Builds and returns an immutable {@link TaskResult} instance.
         *
         * @return the constructed task result
         * @throws IllegalStateException if {@code taskId} or {@code workerId} is {@code null}
         */
        public TaskResult build() {
            if (taskId == null) {
                throw new IllegalStateException("taskId must not be null");
            }
            if (workerId == null) {
                throw new IllegalStateException("workerId must not be null");
            }
            return new TaskResult(this);
        }
    }
}
