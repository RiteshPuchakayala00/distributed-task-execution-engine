package com.engine.master.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Snapshot of metrics at a given point in time.
 *
 * @author Engine Team
 */
public record MetricsSnapshot(
        long totalTasksSubmitted,
        long totalTasksCompleted,
        long totalTasksFailed,
        int currentPendingTasks,
        int activeWorkers,
        double averageLatencyMs,
        double tasksPerSecond
) {}
