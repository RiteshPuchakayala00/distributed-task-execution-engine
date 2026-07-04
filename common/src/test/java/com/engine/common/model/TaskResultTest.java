package com.engine.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskResult Model Tests")
class TaskResultTest {

    @Nested
    @DisplayName("Static Factory Methods")
    class StaticFactories {

        @Test
        @DisplayName("success() creates TaskResult with success=true, result data, and no error message")
        void test_WhenSuccessFactoryUsed_ThenResultIsSuccessful() {
            byte[] resultData = {1, 2, 3};
            TaskResult result = TaskResult.success("task-1", "worker-1", resultData, 500L);

            assertAll("success factory",
                    () -> assertEquals("task-1", result.getTaskId()),
                    () -> assertEquals("worker-1", result.getWorkerId()),
                    () -> assertTrue(result.isSuccess(), "Should be marked as success"),
                    () -> assertArrayEquals(resultData, result.getResult(), "Result data should match"),
                    () -> assertNull(result.getErrorMessage(), "Error message should be null for success"),
                    () -> assertEquals(500L, result.getExecutionTimeMs())
            );
        }

        @Test
        @DisplayName("failure() creates TaskResult with success=false, error message, and null result")
        void test_WhenFailureFactoryUsed_ThenResultIsFailure() {
            TaskResult result = TaskResult.failure("task-2", "worker-2", "OutOfMemoryError", 1200L);

            assertAll("failure factory",
                    () -> assertEquals("task-2", result.getTaskId()),
                    () -> assertEquals("worker-2", result.getWorkerId()),
                    () -> assertFalse(result.isSuccess(), "Should be marked as failure"),
                    () -> assertNull(result.getResult(), "Result should be null for failure"),
                    () -> assertEquals("OutOfMemoryError", result.getErrorMessage()),
                    () -> assertEquals(1200L, result.getExecutionTimeMs())
            );
        }
    }

    @Nested
    @DisplayName("Defensive Copy")
    class DefensiveCopy {

        @Test
        @DisplayName("getResult returns a defensive copy — modifying returned array does not affect TaskResult")
        void test_WhenResultArrayIsModifiedExternally_ThenTaskResultRemainsUnchanged() {
            byte[] originalData = {10, 20, 30};
            TaskResult result = TaskResult.success("task-1", "worker-1", originalData, 100L);

            byte[] retrieved = result.getResult();
            assertNotNull(retrieved, "Result should not be null for success");
            retrieved[0] = 99;

            assertArrayEquals(new byte[]{10, 20, 30}, result.getResult(),
                    "Result data should not be affected by modifications to the returned array");
        }
    }

    @Nested
    @DisplayName("Builder Validation")
    class BuilderValidation {

        @Test
        @DisplayName("Builder throws exception when taskId is null")
        void test_WhenTaskIdIsNull_ThenBuildThrowsException() {
            TaskResult.Builder builder = new TaskResult.Builder()
                    .workerId("worker-1")
                    .success(true);

            assertThrows(Exception.class, builder::build,
                    "Should throw exception when taskId is null");
        }

        @Test
        @DisplayName("Builder throws exception when workerId is null")
        void test_WhenWorkerIdIsNull_ThenBuildThrowsException() {
            TaskResult.Builder builder = new TaskResult.Builder()
                    .taskId("task-1")
                    .success(true);

            assertThrows(Exception.class, builder::build,
                    "Should throw exception when workerId is null");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("TaskResults with same taskId are equal and have same hashCode")
        void test_WhenTaskResultsHaveSameTaskId_ThenTheyAreEqual() {
            TaskResult result1 = TaskResult.success("task-1", "worker-1", new byte[]{1}, 100L);
            TaskResult result2 = TaskResult.failure("task-1", "worker-2", "error", 200L);

            assertAll("equality by taskId",
                    () -> assertEquals(result1, result2, "TaskResults with same taskId should be equal"),
                    () -> assertEquals(result1.hashCode(), result2.hashCode(), "Hash codes should match")
            );
        }

        @Test
        @DisplayName("TaskResults with different taskIds are not equal")
        void test_WhenTaskResultsHaveDifferentTaskIds_ThenTheyAreNotEqual() {
            TaskResult result1 = TaskResult.success("task-1", "worker-1", new byte[]{1}, 100L);
            TaskResult result2 = TaskResult.success("task-2", "worker-1", new byte[]{1}, 100L);

            assertNotEquals(result1, result2,
                    "TaskResults with different taskIds should not be equal");
        }
    }

    @Nested
    @DisplayName("Automatic Timestamps")
    class AutomaticTimestamps {

        @Test
        @DisplayName("completedAt is set automatically when TaskResult is created")
        void test_WhenTaskResultCreated_ThenCompletedAtIsSet() {
            TaskResult result = TaskResult.success("task-1", "worker-1", new byte[]{1}, 100L);

            assertNotNull(result.getCompletedAt(),
                    "completedAt should be set automatically");
        }
    }
}
