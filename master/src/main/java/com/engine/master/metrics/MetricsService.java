package com.engine.master.metrics;

import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks and reports system metrics.
 *
 * @author Engine Team
 */
public class MetricsService implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final TaskManager taskManager;
    private final WorkerRegistry workerRegistry;

    private final LongAdder totalSubmitted = new LongAdder();
    private final LongAdder totalCompleted = new LongAdder();
    private final LongAdder totalFailed = new LongAdder();
    private final LongAdder totalExecutionTimeMs = new LongAdder();

    private volatile long lastReportTime = System.currentTimeMillis();
    private volatile long lastCompletedCount = 0;

    /**
     * Constructs a MetricsService.
     *
     * @param taskManager    the task manager
     * @param workerRegistry the worker registry
     */
    public MetricsService(TaskManager taskManager, WorkerRegistry workerRegistry) {
        this.taskManager = taskManager;
        this.workerRegistry = workerRegistry;
    }

    /**
     * Records a submitted task.
     */
    public void recordTaskSubmitted() {
        totalSubmitted.increment();
    }

    /**
     * Records a completed task.
     *
     * @param executionTimeMs execution time in milliseconds
     */
    public void recordTaskCompleted(long executionTimeMs) {
        totalCompleted.increment();
        totalExecutionTimeMs.add(executionTimeMs);
    }

    /**
     * Records a failed task.
     */
    public void recordTaskFailed() {
        totalFailed.increment();
    }

    /**
     * Gets the current metrics snapshot.
     *
     * @return a metrics snapshot
     */
    public MetricsSnapshot getSnapshot() {
        long completed = totalCompleted.sum();
        long failed = totalFailed.sum();
        long executionTime = totalExecutionTimeMs.sum();
        
        long now = System.currentTimeMillis();
        long timeDiff = now - lastReportTime;
        long completedSinceLast = completed - lastCompletedCount;
        
        double tps = timeDiff > 0 ? (completedSinceLast * 1000.0) / timeDiff : 0.0;
        double avgLatency = completed > 0 ? (double) executionTime / completed : 0.0;
        
        return new MetricsSnapshot(
                totalSubmitted.sum(),
                completed,
                failed,
                taskManager.getPendingCount(),
                workerRegistry.getAvailableWorkers().size(),
                avgLatency,
                tps
        );
    }

    @Override
    public void run() {
        try {
            MetricsSnapshot snapshot = getSnapshot();
            
            logger.info("--- Metrics Report ---");
            logger.info("Active Workers: {}", snapshot.activeWorkers());
            logger.info("Pending Tasks:  {}", snapshot.currentPendingTasks());
            logger.info("Submitted:      {}", snapshot.totalTasksSubmitted());
            logger.info("Completed:      {}", snapshot.totalTasksCompleted());
            logger.info("Failed:         {}", snapshot.totalTasksFailed());
            logger.info("Avg Latency:    {}", String.format("%.2f ms", snapshot.averageLatencyMs()));
            logger.info("Throughput:     {}", String.format("%.2f tasks/sec", snapshot.tasksPerSecond()));
            logger.info("----------------------");

            lastReportTime = System.currentTimeMillis();
            lastCompletedCount = snapshot.totalTasksCompleted();
        } catch (Exception e) {
            logger.error("Error generating metrics report", e);
        }
    }
}
