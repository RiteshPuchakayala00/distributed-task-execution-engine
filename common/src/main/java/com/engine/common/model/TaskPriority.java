package com.engine.common.model;

/**
 * Represents the priority level of a task within the distributed execution engine.
 *
 * <p>Priority levels are ordered numerically from {@link #LOW} (0) to {@link #CRITICAL} (3).
 * Higher numeric values indicate higher scheduling urgency. The task dispatcher uses
 * this priority to determine execution order within its priority queue.</p>
 *
 * @author Engine Team
 */
public enum TaskPriority {

    /** Lowest priority — background or best-effort tasks. */
    LOW(0),

    /** Default priority for standard tasks. */
    MEDIUM(1),

    /** Elevated priority for time-sensitive tasks. */
    HIGH(2),

    /** Highest priority — mission-critical tasks that must be scheduled immediately. */
    CRITICAL(3);

    private final int level;

    /**
     * Constructs a {@code TaskPriority} with the given numeric level.
     *
     * @param level the numeric priority level
     */
    TaskPriority(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric level associated with this priority.
     *
     * <p>Higher values indicate higher priority. This value is used for
     * comparison and ordering in priority-based data structures.</p>
     *
     * @return the numeric priority level
     */
    public int getLevel() {
        return level;
    }
}
