package com.engine.master.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.engine.common.protocol.MessageCodec;
import com.engine.common.protocol.ProtocolConstants;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;

/**
 * Runnable that continuously accepts incoming TCP connections on the
 * server socket and spawns a {@link WorkerConnectionHandler} for each.
 *
 * <p>Each accepted connection is submitted to a shared handler thread pool.
 * The acceptor runs until the {@code running} flag is cleared or the server
 * socket is closed.</p>
 *
 * @author Engine Team
 */
public class ConnectionAcceptor implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionAcceptor.class);

    private final ServerSocket serverSocket;
    private final ConnectionManager connectionManager;
    private final WorkerRegistry workerRegistry;
    private final MessageCodec messageCodec;
    private final ExecutorService handlerPool;
    private final AtomicBoolean running;
    private final TaskManager taskManager;

    /**
     * Creates a new connection acceptor.
     *
     * @param serverSocket      the server socket to accept on (must not be {@code null})
     * @param connectionManager the connection manager (must not be {@code null})
     * @param workerRegistry    the worker registry (must not be {@code null})
     * @param messageCodec      the message codec (must not be {@code null})
     * @param handlerPool       the thread pool for handler execution (must not be {@code null})
     * @param running           shared running flag (must not be {@code null})
     * @throws NullPointerException if any parameter is {@code null}
     */
    public ConnectionAcceptor(ServerSocket serverSocket,
                              ConnectionManager connectionManager,
                              WorkerRegistry workerRegistry,
                              MessageCodec messageCodec,
                              ExecutorService handlerPool,
                              AtomicBoolean running,
                              TaskManager taskManager) {
        this.serverSocket = Objects.requireNonNull(serverSocket, "serverSocket must not be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.workerRegistry = Objects.requireNonNull(workerRegistry, "workerRegistry must not be null");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec must not be null");
        this.handlerPool = Objects.requireNonNull(handlerPool, "handlerPool must not be null");
        this.running = Objects.requireNonNull(running, "running must not be null");
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager must not be null");
    }

    /**
     * Runs the accept loop, blocking on {@link ServerSocket#accept()} until
     * the running flag is cleared or the server socket is closed.
     */
    @Override
    public void run() {
        Thread.currentThread().setName("connection-acceptor");
        logger.info("Connection acceptor started, listening on {}", serverSocket.getLocalSocketAddress());

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                configureSocket(clientSocket);

                WorkerConnectionHandler handler = new WorkerConnectionHandler(
                        clientSocket, messageCodec, connectionManager, workerRegistry, taskManager);
                handlerPool.submit(handler);

                logger.info("Accepted connection from {}", clientSocket.getRemoteSocketAddress());
            } catch (SocketException e) {
                if (!running.get()) {
                    logger.info("Acceptor stopping (server socket closed)");
                    break;
                }
                logger.error("Socket error while accepting connection", e);
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
        logger.info("Connection acceptor stopped");
    }

    /**
     * Configures a newly-accepted socket with optimal settings.
     *
     * @param socket the socket to configure
     * @throws SocketException if a socket option cannot be set
     */
    private void configureSocket(Socket socket) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(ProtocolConstants.DEFAULT_SOCKET_TIMEOUT_MS);
    }
}
