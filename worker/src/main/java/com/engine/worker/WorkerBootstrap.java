package com.engine.worker;

import com.engine.worker.config.WorkerConfig;
import com.engine.worker.executor.TaskExecutor;
import com.engine.worker.network.HeartbeatTask;
import com.engine.worker.network.WorkerClient;
import com.engine.worker.network.ReconnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstraps the worker node.
 *
 * @author Engine Team
 */
public class WorkerBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(WorkerBootstrap.class);

    private final WorkerConfig config;
    private final WorkerClient client;
    private final TaskExecutor taskExecutor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Constructs a WorkerBootstrap.
     *
     * @param config the worker configuration
     */
    public WorkerBootstrap(WorkerConfig config) {
        this.config = config;
        this.taskExecutor = new TaskExecutor(config);
        this.client = new WorkerClient(config, taskExecutor);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts the worker.
     *
     * @throws Exception if an error occurs during startup
     */
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Worker is already running");
            return;
        }

        logger.info("Starting WorkerBootstrap");

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "WorkerShutdownHook"));

        client.connectAndRegister();
        client.startReadLoop();

        scheduler.scheduleAtFixedRate(
                new HeartbeatTask(client),
                config.getHeartbeatIntervalMs(),
                config.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        Thread supervisorThread = new Thread(this::supervisorLoop, "WorkerSupervisor");
        supervisorThread.setUncaughtExceptionHandler((t, e) -> {
            logger.error("Fatal error in WorkerSupervisor thread. Shutting down JVM.", e);
            System.exit(1);
        });
        supervisorThread.setDaemon(false);
        supervisorThread.start();

        logger.info("Worker started successfully");
    }

    private void supervisorLoop() {
        while (running.get()) {
            if (!client.isActive()) {
                logger.warn("Worker connection lost. Attempting to reconnect...");
                ReconnectHandler reconnector = new ReconnectHandler(client, 10, 1000);
                if (reconnector.attemptReconnect()) {
                    client.startReadLoop();
                } else {
                    logger.error("Failed to reconnect after maximum attempts. Shutting down.");
                    shutdown();
                    System.exit(1);
                    break;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Shuts down the worker.
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            logger.info("Shutting down WorkerBootstrap");
            scheduler.shutdownNow();
            client.disconnect();
            taskExecutor.shutdown();
            logger.info("Worker shut down completed");
        }
    }
}
