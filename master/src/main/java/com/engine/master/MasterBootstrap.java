package com.engine.master;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.protocol.MessageCodec;
import com.engine.master.config.MasterConfig;
import com.engine.master.network.ConnectionAcceptor;
import com.engine.master.network.ConnectionManager;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;
import com.engine.master.task.TaskScheduler;
import com.engine.master.heartbeat.HeartbeatMonitor;
import com.engine.master.recovery.FailureRecoveryManager;
import com.engine.master.metrics.MetricsService;

/**
 * Wires together all master-server components and manages the overall server
 * lifecycle (start / shutdown).
 *
 * <p>Implements {@link AutoCloseable} so it can be used in try-with-resources
 * blocks.</p>
 *
 * @author Engine Team
 */
public class MasterBootstrap implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MasterBootstrap.class);

    private final MasterConfig config;
    private final ServerSocket serverSocket;
    private final ConnectionManager connectionManager;
    private final WorkerRegistry workerRegistry;
    private final MessageCodec messageCodec;
    private final ExecutorService acceptorExecutor;
    private final ExecutorService handlerPool;
    private final ConnectionAcceptor connectionAcceptor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final TaskManager taskManager;
    private final FailureRecoveryManager recoveryManager;
    private final HeartbeatMonitor heartbeatMonitor;
    private final MetricsService metricsService;
    private final java.util.concurrent.ScheduledExecutorService schedulerExecutor;

    /**
     * Creates a new bootstrap with the given configuration.
     *
     * <p>All components are instantiated but the server is <strong>not</strong>
     * started until {@link #start()} is called.</p>
     *
     * @param config the master configuration (must not be {@code null})
     * @throws IOException          if the server socket cannot be created
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public MasterBootstrap(MasterConfig config) throws IOException {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.serverSocket = new ServerSocket();
        this.connectionManager = new ConnectionManager();
        this.workerRegistry = new WorkerRegistry();
        this.messageCodec = MessageCodec.createDefault();
        this.acceptorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acceptor");
            t.setDaemon(true);
            return t;
        });
        this.handlerPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        this.taskManager = new TaskManager(config.getTaskQueueCapacity());
        this.schedulerExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "master-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.metricsService = new MetricsService(taskManager, workerRegistry);
        this.taskManager.setMetricsService(metricsService);
        
        this.recoveryManager = new FailureRecoveryManager(taskManager, workerRegistry, connectionManager);
        this.heartbeatMonitor = new HeartbeatMonitor(workerRegistry, recoveryManager, 9000, 15000);
        this.connectionAcceptor = new ConnectionAcceptor(
                serverSocket, connectionManager, workerRegistry, messageCodec, handlerPool, running, taskManager);
    }

    /**
     * Binds the server socket and starts accepting worker connections.
     *
     * @throws IOException           if the socket cannot be bound
     * @throws IllegalStateException if the server is already running
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Master server is already running");
        }

        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.getBindHost(), config.getBindPort()));

        acceptorExecutor.submit(connectionAcceptor);
        
        TaskScheduler scheduler = new TaskScheduler(taskManager, workerRegistry, connectionManager);
        schedulerExecutor.scheduleAtFixedRate(scheduler, 1000, config.getSchedulerIntervalMs(), TimeUnit.MILLISECONDS);
        schedulerExecutor.scheduleAtFixedRate(heartbeatMonitor, 2000, config.getHeartbeatCheckIntervalMs(), TimeUnit.MILLISECONDS);
        schedulerExecutor.scheduleAtFixedRate(metricsService, 10000, config.getMetricsReportIntervalMs(), TimeUnit.MILLISECONDS);
        schedulerExecutor.scheduleAtFixedRate(() -> taskManager.sweepTerminalTasks(300000), 60000, 60000, TimeUnit.MILLISECONDS); // Sweep every minute, keeping tasks for 5 mins

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "master-shutdown-hook"));

        logger.info("Master server started on {}:{}", config.getBindHost(), config.getBindPort());
        logger.info("Configuration: {}", config);
    }

    /**
     * Gracefully shuts down the master server, closing all connections and
     * stopping all thread pools.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        logger.info("Master server shutting down...");

        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.warn("Error closing server socket: {}", e.getMessage());
        }

        connectionManager.closeAll();
        acceptorExecutor.shutdownNow();
        handlerPool.shutdownNow();
        schedulerExecutor.shutdownNow();

        try {
            if (!handlerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Handler pool did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Master server shutdown complete.");
    }

    /**
     * Delegates to {@link #shutdown()}.
     */
    @Override
    public void close() {
        shutdown();
    }

    // ── Getters ──────────────────────────────────────────────────────────

    /**
     * Returns the master configuration.
     *
     * @return the {@link MasterConfig}
     */
    public MasterConfig getConfig() {
        return config;
    }

    /**
     * Returns the worker registry.
     *
     * @return the {@link WorkerRegistry}
     */
    public WorkerRegistry getWorkerRegistry() {
        return workerRegistry;
    }

    /**
     * Returns the connection manager.
     *
     * @return the {@link ConnectionManager}
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return {@code true} if the server is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the task manager.
     *
     * @return the {@link TaskManager}
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Returns the local port the server socket is bound to.
     *
     * @return the server port, or {@code -1} if not yet bound
     */
    public int getServerPort() {
        return serverSocket.getLocalPort();
    }
}
