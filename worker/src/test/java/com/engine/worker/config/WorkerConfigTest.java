package com.engine.worker.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("WorkerConfig Tests")
class WorkerConfigTest {

    @BeforeEach
    void setUp() {
        System.clearProperty("worker.master.host");
        System.clearProperty("worker.master.port");
        System.clearProperty("worker.socket.timeout.ms");
        System.clearProperty("worker.heartbeat.interval.ms");
        System.clearProperty("worker.executor.core.pool.size");
        System.clearProperty("worker.executor.max.pool.size");
        System.clearProperty("worker.executor.keep.alive.seconds");
        System.clearProperty("worker.executor.queue.capacity");
    }

    @AfterEach
    void tearDown() {
        setUp(); // Clear again
    }

    @Nested
    @DisplayName("Configuration Loading Tests")
    class ConfigLoadingTests {
        
        @Test
        @DisplayName("Default config has correct values")
        void test_WhenDefaultConfig_ThenHasCorrectValues() {
            WorkerConfig config = WorkerConfig.loadDefault();
            int cores = Runtime.getRuntime().availableProcessors();
            assertAll(
                () -> assertEquals("127.0.0.1", config.getMasterHost()),
                () -> assertEquals(9090, config.getMasterPort()),
                () -> assertEquals(30000, config.getSocketTimeoutMs()),
                () -> assertEquals(5000, config.getHeartbeatIntervalMs()),
                () -> assertEquals(cores, config.getCorePoolSize()),
                () -> assertEquals(cores * 4, config.getMaxPoolSize()),
                () -> assertEquals(60, config.getKeepAliveSeconds()),
                () -> assertEquals(1000, config.getQueueCapacity())
            );
        }

        @Test
        @DisplayName("Config from System Properties overrides defaults")
        void test_WhenSystemPropertiesProvided_ThenOverridesDefaults() {
            System.setProperty("worker.master.host", "192.168.1.100");
            System.setProperty("worker.master.port", "8080");
            System.setProperty("worker.executor.core.pool.size", "20");
            System.setProperty("worker.heartbeat.interval.ms", "3000");

            WorkerConfig config = WorkerConfig.loadDefault();
            assertAll(
                () -> assertEquals("192.168.1.100", config.getMasterHost()),
                () -> assertEquals(8080, config.getMasterPort()),
                () -> assertEquals(20, config.getCorePoolSize()),
                () -> assertEquals(3000, config.getHeartbeatIntervalMs())
            );
        }

        @Test
        @DisplayName("Config from properties file overrides defaults")
        void test_WhenFileProvided_ThenOverridesDefaults() throws IOException {
            File tempFile = Files.createTempFile("worker-test", ".properties").toFile();
            tempFile.deleteOnExit();

            Properties props = new Properties();
            props.setProperty("worker.master.host", "10.0.0.1");
            props.setProperty("worker.master.port", "7070");
            
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                props.store(out, null);
            }

            WorkerConfig config = WorkerConfig.load(tempFile.getAbsolutePath());
            assertAll(
                () -> assertEquals("10.0.0.1", config.getMasterHost()),
                () -> assertEquals(7070, config.getMasterPort())
            );
        }

        @Test
        @DisplayName("Invalid number throws NumberFormatException")
        void test_WhenInvalidNumber_ThenThrowsException() {
            System.setProperty("worker.master.port", "invalid");
            assertThrows(NumberFormatException.class, WorkerConfig::loadDefault);
        }

        @Test
        @DisplayName("toString is non-null")
        void test_WhenToStringCalled_ThenReturnsNonNull() {
            WorkerConfig config = WorkerConfig.loadDefault();
            assertNotNull(config.toString());
        }
    }
}
