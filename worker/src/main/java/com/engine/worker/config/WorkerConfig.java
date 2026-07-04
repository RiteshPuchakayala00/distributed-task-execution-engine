package com.engine.worker.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Immutable config object loaded from properties.
 *
 * @author Engine Team
 */
public class WorkerConfig {

    private final String masterHost;
    private final int masterPort;
    private final int socketTimeoutMs;
    private final int heartbeatIntervalMs;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int keepAliveSeconds;
    private final int queueCapacity;

    /**
     * Constructs a new WorkerConfig.
     *
     * @param masterHost          the master host
     * @param masterPort          the master port
     * @param socketTimeoutMs     the socket timeout in milliseconds
     * @param heartbeatIntervalMs the heartbeat interval in milliseconds
     * @param corePoolSize        the core pool size
     * @param maxPoolSize         the max pool size
     * @param keepAliveSeconds    the keep alive seconds
     * @param queueCapacity       the queue capacity
     */
    private WorkerConfig(String masterHost, int masterPort, int socketTimeoutMs, int heartbeatIntervalMs,
                         int corePoolSize, int maxPoolSize, int keepAliveSeconds, int queueCapacity) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.socketTimeoutMs = socketTimeoutMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveSeconds = keepAliveSeconds;
        this.queueCapacity = queueCapacity;
    }

    /**
     * Loads the worker configuration from the given properties file path.
     *
     * @param path the path to the properties file
     * @return the WorkerConfig instance
     * @throws IOException if an I/O error occurs
     */
    public static WorkerConfig load(String path) throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(path)) {
            props.load(in);
        }

        int cores = Runtime.getRuntime().availableProcessors();

        String masterHost = props.getProperty("worker.master.host", System.getProperty("worker.master.host", "127.0.0.1"));
        int masterPort = Integer.parseInt(props.getProperty("worker.master.port", System.getProperty("worker.master.port", "9090")));
        int socketTimeoutMs = Integer.parseInt(props.getProperty("worker.socket.timeout.ms", System.getProperty("worker.socket.timeout.ms", "30000")));
        int heartbeatIntervalMs = Integer.parseInt(props.getProperty("worker.heartbeat.interval.ms", System.getProperty("worker.heartbeat.interval.ms", "5000")));
        int corePoolSize = Integer.parseInt(props.getProperty("worker.executor.core.pool.size", System.getProperty("worker.executor.core.pool.size", String.valueOf(cores))));
        int maxPoolSize = Integer.parseInt(props.getProperty("worker.executor.max.pool.size", System.getProperty("worker.executor.max.pool.size", String.valueOf(cores * 4))));
        int keepAliveSeconds = Integer.parseInt(props.getProperty("worker.executor.keep.alive.seconds", System.getProperty("worker.executor.keep.alive.seconds", "60")));
        int queueCapacity = Integer.parseInt(props.getProperty("worker.executor.queue.capacity", System.getProperty("worker.executor.queue.capacity", "1000")));

        return new WorkerConfig(masterHost, masterPort, socketTimeoutMs, heartbeatIntervalMs, corePoolSize, maxPoolSize, keepAliveSeconds, queueCapacity);
    }

    /**
     * Loads the default worker configuration.
     *
     * @return the WorkerConfig instance
     */
    public static WorkerConfig loadDefault() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new WorkerConfig(
                System.getProperty("worker.master.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("worker.master.port", "9090")),
                Integer.parseInt(System.getProperty("worker.socket.timeout.ms", "30000")),
                Integer.parseInt(System.getProperty("worker.heartbeat.interval.ms", "5000")),
                Integer.parseInt(System.getProperty("worker.executor.core.pool.size", String.valueOf(cores))),
                Integer.parseInt(System.getProperty("worker.executor.max.pool.size", String.valueOf(cores * 4))),
                Integer.parseInt(System.getProperty("worker.executor.keep.alive.seconds", "60")),
                Integer.parseInt(System.getProperty("worker.executor.queue.capacity", "1000"))
        );
    }

    /**
     * Gets the master host.
     *
     * @return the master host
     */
    public String getMasterHost() {
        return masterHost;
    }

    /**
     * Gets the master port.
     *
     * @return the master port
     */
    public int getMasterPort() {
        return masterPort;
    }

    /**
     * Gets the socket timeout in milliseconds.
     *
     * @return the socket timeout in milliseconds
     */
    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    /**
     * Gets the heartbeat interval in milliseconds.
     *
     * @return the heartbeat interval in milliseconds
     */
    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * Gets the core pool size.
     *
     * @return the core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Gets the max pool size.
     *
     * @return the max pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Gets the keep alive seconds.
     *
     * @return the keep alive seconds
     */
    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /**
     * Gets the queue capacity.
     *
     * @return the queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }
}
