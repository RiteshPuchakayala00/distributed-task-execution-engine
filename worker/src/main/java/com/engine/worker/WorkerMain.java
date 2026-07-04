package com.engine.worker;

import com.engine.worker.config.WorkerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the worker node.
 *
 * @author Engine Team
 */
public class WorkerMain {

    private static final Logger logger = LoggerFactory.getLogger(WorkerMain.class);

    /**
     * Main method.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            WorkerConfig config;
            if (args.length > 0) {
                config = WorkerConfig.load(args[0]);
            } else {
                config = WorkerConfig.loadDefault();
            }

            WorkerBootstrap bootstrap = new WorkerBootstrap(config);
            bootstrap.start();

            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.warn("WorkerMain interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start worker", e);
            System.exit(1);
        }
    }
}
