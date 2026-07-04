package com.engine.common.model;

/**
 * Represents the lifecycle status of a task within the distributed execution engine.
 *
 * <p>A task progresses through these states during its lifetime. Terminal states
 * ({@link #COMPLETED}, {@link #FAILED}, {@link #TIMED_OUT}, {@link #CANCELLED})
 * indicate that the task has reached a final disposition and will not transition
 * to any other state.</p>
 *
 * @author Engine Team
 */
public enum TaskStatus {

    /** The task has been submitted but not yet assigned to a worker. */
    PENDING,

    /** The task has been assigned to a worker but execution has not started. */
    ASSIGNED,

    /** The task is currently being executed by a worker. */
    RUNNING,

    /** The task has completed execution successfully. This is a terminal state. */
    COMPLETED,

    /** The task has failed during execution. This is a terminal state. */
    FAILED,

    /** The task exceeded its allowed execution time. This is a terminal state. */
    TIMED_OUT,

    /** The task was explicitly cancelled before completion. This is a terminal state. */
    CANCELLED;

    /**
     * Determines whether this status represents a terminal (final) state.
     *
     * <p>A terminal state means the task will not undergo any further state transitions.
     * Terminal states are: {@code COMPLETED}, {@code FAILED}, {@code TIMED_OUT},
     * and {@code CANCELLED}.</p>
     *
     * @return {@code true} if this status is terminal, {@code false} otherwise
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMED_OUT || this == CANCELLED;
    }
}
