package com.engine.worker.executor;

import com.engine.common.model.Task;
import com.engine.worker.config.WorkerConfig;
import com.engine.worker.network.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper for ThreadPoolExecutor.
 *
 * @author Engine Team
 */
public class TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private final ThreadPoolExecutor executor;

    /**
     * Constructs a TaskExecutor.
     *
     * @param config the worker configuration
     */
    public TaskExecutor(WorkerConfig config) {
        this.executor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getQueueCapacity()),
                new CustomThreadFactory("worker-exec-")
        );
    }

    /**
     * Submits a task for execution.
     *
     * @param task   the task to execute
     * @param client the worker client to send results back to
     */
    public void submit(Task task, WorkerClient client) {
        executor.submit(new TaskRunner(task, client));
        logger.debug("Submitted task {} to thread pool", task.getTaskId());
    }

    /**
     * Initiates a graceful shutdown of the executor.
     */
    public void shutdown() {
        logger.info("Shutting down TaskExecutor");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("TaskExecutor did not terminate in 10 seconds, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("TaskExecutor shutdown interrupted", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the number of active tasks.
     *
     * @return the active task count
     */
    public int getActiveTaskCount() {
        return executor.getActiveCount();
    }

    /**
     * Custom thread factory for naming threads.
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        public CustomThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
