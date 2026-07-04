package com.engine.worker.executor;

import com.engine.worker.config.WorkerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("TaskExecutor Tests")
class TaskExecutorTest {

    private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        WorkerConfig config = WorkerConfig.loadDefault();
        taskExecutor = new TaskExecutor(config);
    }
    
    @AfterEach
    void tearDown() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
    }

    @Test
    @DisplayName("Initialized executor has zero active tasks")
    void test_WhenInitialized_ThenActiveCountIsZero() {
        assertEquals(0, taskExecutor.getActiveTaskCount());
    }

    @Test
    @DisplayName("Shutdown completes without errors")
    void test_WhenShutdown_ThenExecutorTerminates() {
        assertDoesNotThrow(() -> taskExecutor.shutdown());
    }
}
