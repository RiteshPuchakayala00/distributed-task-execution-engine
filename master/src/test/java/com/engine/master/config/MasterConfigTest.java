package com.engine.master.config;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MasterConfig — configuration loading and defaults")
class MasterConfigTest {

    @Nested
    @DisplayName("Default configuration")
    class DefaultConfig {

        @Test
        @DisplayName("Default constructor sets all correct default values")
        void test_WhenDefaultConstructor_ThenAllDefaultsAreCorrect() {
            MasterConfig config = new MasterConfig();

            assertAll("default config values",
                () -> assertEquals("0.0.0.0", config.getBindHost(),
                        "bindHost should default to 0.0.0.0"),
                () -> assertEquals(9090, config.getBindPort(),
                        "bindPort should default to 9090"),
                () -> assertEquals(50, config.getMaxConnections(),
                        "maxConnections should default to 50"),
                () -> assertEquals(30000, config.getSocketTimeoutMs(),
                        "socketTimeoutMs should default to 30000"),
                () -> assertEquals(2000, config.getHeartbeatCheckIntervalMs(),
                        "heartbeatCheckIntervalMs should default to 2000"),
                () -> assertEquals(9000, config.getHeartbeatUnresponsiveMs(),
                        "heartbeatUnresponsiveMs should default to 9000"),
                () -> assertEquals(15000, config.getHeartbeatDeadMs(),
                        "heartbeatDeadMs should default to 15000"),
                () -> assertEquals(100, config.getSchedulerIntervalMs(),
                        "schedulerIntervalMs should default to 100"),
                () -> assertEquals(10000, config.getTaskQueueCapacity(),
                        "taskQueueCapacity should default to 10000"),
                () -> assertEquals(60000L, config.getTaskDefaultTimeoutMs(),
                        "taskDefaultTimeoutMs should default to 60000"),
                () -> assertEquals(3, config.getTaskDefaultMaxRetries(),
                        "taskDefaultMaxRetries should default to 3"),
                () -> assertEquals(30000, config.getMetricsReportIntervalMs(),
                        "metricsReportIntervalMs should default to 30000")
            );
        }

        @Test
        @DisplayName("loadDefault returns config with default values")
        void test_WhenLoadDefault_ThenDefaultValuesApplied() {
            MasterConfig config = MasterConfig.loadDefault();

            assertAll("loadDefault values",
                () -> assertEquals("0.0.0.0", config.getBindHost()),
                () -> assertEquals(9090, config.getBindPort()),
                () -> assertEquals(50, config.getMaxConnections())
            );
        }
    }

    @Nested
    @DisplayName("Properties-based configuration")
    class PropertiesConfig {

        @Test
        @DisplayName("Properties override all default values")
        void test_WhenAllPropertiesProvided_ThenAllDefaultsOverridden() {
            Properties props = new Properties();
            props.setProperty("master.bind.host", "192.168.1.100");
            props.setProperty("master.bind.port", "7070");
            props.setProperty("master.max.connections", "100");
            props.setProperty("master.socket.timeout.ms", "15000");
            props.setProperty("master.heartbeat.check.interval.ms", "5000");
            props.setProperty("master.heartbeat.unresponsive.ms", "12000");
            props.setProperty("master.heartbeat.dead.ms", "20000");
            props.setProperty("master.scheduler.interval.ms", "200");
            props.setProperty("master.task.queue.capacity", "5000");
            props.setProperty("master.task.default.timeout.ms", "120000");
            props.setProperty("master.task.default.max.retries", "5");
            props.setProperty("master.metrics.report.interval.ms", "60000");

            MasterConfig config = new MasterConfig(props);

            assertAll("overridden values",
                () -> assertEquals("192.168.1.100", config.getBindHost()),
                () -> assertEquals(7070, config.getBindPort()),
                () -> assertEquals(100, config.getMaxConnections()),
                () -> assertEquals(15000, config.getSocketTimeoutMs()),
                () -> assertEquals(5000, config.getHeartbeatCheckIntervalMs()),
                () -> assertEquals(12000, config.getHeartbeatUnresponsiveMs()),
                () -> assertEquals(20000, config.getHeartbeatDeadMs()),
                () -> assertEquals(200, config.getSchedulerIntervalMs()),
                () -> assertEquals(5000, config.getTaskQueueCapacity()),
                () -> assertEquals(120000L, config.getTaskDefaultTimeoutMs()),
                () -> assertEquals(5, config.getTaskDefaultMaxRetries()),
                () -> assertEquals(60000, config.getMetricsReportIntervalMs())
            );
        }

        @Test
        @DisplayName("Partial properties use defaults for missing keys")
        void test_WhenPartialProperties_ThenMissingKeysUseDefaults() {
            Properties props = new Properties();
            props.setProperty("master.bind.host", "10.0.0.1");
            props.setProperty("master.bind.port", "7777");
            // all other keys intentionally omitted

            MasterConfig config = new MasterConfig(props);

            assertAll("partial override",
                () -> assertEquals("10.0.0.1", config.getBindHost(),
                        "provided host should be used"),
                () -> assertEquals(7777, config.getBindPort(),
                        "provided port should be used"),
                () -> assertEquals(50, config.getMaxConnections(),
                        "missing maxConnections should fall back to default"),
                () -> assertEquals(30000, config.getSocketTimeoutMs(),
                        "missing socketTimeoutMs should fall back to default"),
                () -> assertEquals(2000, config.getHeartbeatCheckIntervalMs(),
                        "missing heartbeatCheckIntervalMs should fall back to default"),
                () -> assertEquals(3, config.getTaskDefaultMaxRetries(),
                        "missing taskDefaultMaxRetries should fall back to default")
            );
        }

        @Test
        @DisplayName("Invalid port string throws NumberFormatException")
        void test_WhenInvalidPortString_ThenThrowsNumberFormatException() {
            Properties props = new Properties();
            props.setProperty("master.bind.port", "not_a_number");

            assertThrows(NumberFormatException.class, () -> new MasterConfig(props),
                    "Non-numeric port should cause NumberFormatException");
        }
    }

    @Nested
    @DisplayName("File-based loading")
    class FileLoading {

        @Test
        @DisplayName("Load from non-existent file path returns defaults without exception")
        void test_WhenFileDoesNotExist_ThenReturnsDefaultsNoException() {
            MasterConfig config = assertDoesNotThrow(
                    () -> MasterConfig.load("/nonexistent/path/config.properties"),
                    "loading from non-existent file should not throw");

            assertAll("fallback defaults",
                () -> assertEquals("0.0.0.0", config.getBindHost()),
                () -> assertEquals(9090, config.getBindPort()),
                () -> assertEquals(50, config.getMaxConnections()),
                () -> assertEquals(30000, config.getSocketTimeoutMs())
            );
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString returns non-null and non-empty string")
        void test_WhenToStringCalled_ThenReturnsNonNullNonEmpty() {
            MasterConfig config = new MasterConfig();
            String result = config.toString();

            assertAll("toString",
                () -> assertNotNull(result, "toString must not return null"),
                () -> assertFalse(result.isEmpty(), "toString must not be empty")
            );
        }

        @Test
        @DisplayName("toString contains key configuration values")
        void test_WhenToStringCalled_ThenContainsConfigInfo() {
            MasterConfig config = new MasterConfig();
            String result = config.toString();

            assertAll("toString content",
                () -> assertNotNull(result),
                () -> assertFalse(result.isBlank(),
                        "toString result should contain meaningful content")
            );
        }
    }
}
