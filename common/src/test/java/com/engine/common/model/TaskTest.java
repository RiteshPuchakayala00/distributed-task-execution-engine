package com.engine.common.model;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Task Model Tests")
class TaskTest {

    @Nested
    @DisplayName("Builder Default Values")
    class BuilderDefaults {

        @Test
        @DisplayName("Builder creates task with auto-generated taskId, PENDING status, MEDIUM priority, and sensible defaults")
        void test_WhenBuilderUsedWithMinimalConfig_ThenDefaultsAreApplied() {
            Task task = new Task.Builder()
                    .taskType("compute")
                    .build();

            assertAll("default values",
                    () -> assertNotNull(task.getTaskId(), "taskId should be auto-generated"),
                    () -> assertDoesNotThrow(() -> UUID.fromString(task.getTaskId()), "taskId should be a valid UUID"),
                    () -> assertEquals("compute", task.getTaskType()),
                    () -> assertEquals(TaskStatus.PENDING, task.getStatus()),
                    () -> assertEquals(TaskPriority.MEDIUM, task.getPriority()),
                    () -> assertEquals(3, task.getMaxRetries()),
                    () -> assertEquals(60000L, task.getTimeoutMs()),
                    () -> assertEquals(0, task.getRetryCount()),
                    () -> assertNotNull(task.getCreatedAt(), "createdAt should be set automatically"),
                    () -> assertNull(task.getAssignedWorkerId(), "assignedWorkerId should be null by default")
            );
        }
    }

    @Nested
    @DisplayName("Builder Copy Constructor")
    class BuilderCopyConstructor {

        @Test
        @DisplayName("Builder(Task) copies all fields from existing task")
        void test_WhenBuilderCopiesExistingTask_ThenAllFieldsArePreserved() {
            byte[] payload = {1, 2, 3, 4, 5};
            Task original = new Task.Builder()
                    .taskType("analysis")
                    .payload(payload)
                    .priority(TaskPriority.HIGH)
                    .status(TaskStatus.RUNNING)
                    .maxRetries(5)
                    .retryCount(2)
                    .timeoutMs(30000L)
                    .assignedWorkerId("worker-1")
                    .build();

            Task copy = new Task.Builder(original).build();

            assertAll("copied fields",
                    () -> assertEquals(original.getTaskId(), copy.getTaskId()),
                    () -> assertEquals(original.getTaskType(), copy.getTaskType()),
                    () -> assertArrayEquals(original.getPayload(), copy.getPayload()),
                    () -> assertEquals(original.getPriority(), copy.getPriority()),
                    () -> assertEquals(original.getStatus(), copy.getStatus()),
                    () -> assertEquals(original.getMaxRetries(), copy.getMaxRetries()),
                    () -> assertEquals(original.getRetryCount(), copy.getRetryCount()),
                    () -> assertEquals(original.getTimeoutMs(), copy.getTimeoutMs()),
                    () -> assertEquals(original.getCreatedAt(), copy.getCreatedAt()),
                    () -> assertEquals(original.getAssignedWorkerId(), copy.getAssignedWorkerId())
            );
        }
    }

    @Nested
    @DisplayName("Builder Validation")
    class BuilderValidation {

        @Test
        @DisplayName("Builder throws IllegalStateException when taskType is null")
        void test_WhenTaskTypeIsNull_ThenThrowsIllegalStateException() {
            Task.Builder builder = new Task.Builder();
            // taskType not set (null)

            assertThrows(IllegalStateException.class, builder::build,
                    "Should throw IllegalStateException when taskType is null");
        }

        @Test
        @DisplayName("Builder throws IllegalStateException when taskType is blank")
        void test_WhenTaskTypeIsBlank_ThenThrowsIllegalStateException() {
            Task.Builder builder = new Task.Builder()
                    .taskType("   ");

            assertThrows(IllegalStateException.class, builder::build,
                    "Should throw IllegalStateException when taskType is blank");
        }

        @Test
        @DisplayName("Builder throws IllegalStateException when maxRetries is negative")
        void test_WhenMaxRetriesIsNegative_ThenThrowsIllegalStateException() {
            Task.Builder builder = new Task.Builder()
                    .taskType("compute")
                    .maxRetries(-1);

            assertThrows(IllegalStateException.class, builder::build,
                    "Should throw IllegalStateException when maxRetries is negative");
        }

        @Test
        @DisplayName("Builder throws IllegalStateException when timeoutMs is zero")
        void test_WhenTimeoutMsIsZero_ThenThrowsIllegalStateException() {
            Task.Builder builder = new Task.Builder()
                    .taskType("compute")
                    .timeoutMs(0);

            assertThrows(IllegalStateException.class, builder::build,
                    "Should throw IllegalStateException when timeoutMs is zero");
        }
    }

    @Nested
    @DisplayName("Payload Defensive Copy")
    class PayloadDefensiveCopy {

        @Test
        @DisplayName("getPayload returns a defensive copy — modifying returned array does not affect task")
        void test_WhenPayloadArrayIsModifiedExternally_ThenTaskPayloadRemainsUnchanged() {
            byte[] original = {10, 20, 30};
            Task task = new Task.Builder()
                    .taskType("compute")
                    .payload(original)
                    .build();

            byte[] retrieved = task.getPayload();
            retrieved[0] = 99;

            assertArrayEquals(new byte[]{10, 20, 30}, task.getPayload(),
                    "Payload should not be affected by modifications to the returned array");
        }
    }

    @Nested
    @DisplayName("Comparable (compareTo)")
    class ComparableTests {

        @Test
        @DisplayName("CRITICAL priority task sorts before LOW priority task")
        void test_WhenComparingCriticalVsLow_ThenCriticalComesFirst() throws InterruptedException {
            Task critical = new Task.Builder()
                    .taskType("compute")
                    .priority(TaskPriority.CRITICAL)
                    .build();

            // Small delay to ensure different createdAt
            Thread.sleep(10);

            Task low = new Task.Builder()
                    .taskType("compute")
                    .priority(TaskPriority.LOW)
                    .build();

            assertTrue(critical.compareTo(low) < 0,
                    "CRITICAL priority task should come before LOW priority task");
        }

        @Test
        @DisplayName("Same priority — earlier createdAt sorts first")
        void test_WhenSamePriority_ThenEarlierCreatedAtComesFirst() throws InterruptedException {
            Task first = new Task.Builder()
                    .taskType("compute")
                    .priority(TaskPriority.MEDIUM)
                    .build();

            // Small delay to guarantee different createdAt
            Thread.sleep(10);

            Task second = new Task.Builder()
                    .taskType("compute")
                    .priority(TaskPriority.MEDIUM)
                    .build();

            assertTrue(first.compareTo(second) < 0,
                    "Task created earlier should come before task created later when priorities are equal");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("Tasks with same taskId are equal")
        void test_WhenTasksHaveSameTaskId_ThenTheyAreEqual() {
            String sharedId = UUID.randomUUID().toString();

            Task task1 = new Task.Builder()
                    .taskId(sharedId)
                    .taskType("compute")
                    .build();

            Task task2 = new Task.Builder()
                    .taskId(sharedId)
                    .taskType("analysis")
                    .priority(TaskPriority.HIGH)
                    .build();

            assertAll("equality by taskId",
                    () -> assertEquals(task1, task2),
                    () -> assertEquals(task1.hashCode(), task2.hashCode())
            );
        }

        @Test
        @DisplayName("Tasks with different taskIds are not equal")
        void test_WhenTasksHaveDifferentTaskIds_ThenTheyAreNotEqual() {
            Task task1 = new Task.Builder()
                    .taskType("compute")
                    .build();

            Task task2 = new Task.Builder()
                    .taskType("compute")
                    .build();

            assertNotEquals(task1, task2,
                    "Tasks with different auto-generated taskIds should not be equal");
        }
    }

    @Nested
    @DisplayName("Copy Methods (with*)")
    class CopyMethods {

        @Test
        @DisplayName("withStatus returns new Task with updated status, original unchanged")
        void test_WhenWithStatusCalled_ThenNewTaskHasUpdatedStatusAndOriginalIsUnchanged() {
            Task original = new Task.Builder()
                    .taskType("compute")
                    .build();

            Task updated = original.withStatus(TaskStatus.RUNNING);

            assertAll("withStatus copy",
                    () -> assertNotSame(original, updated, "Should return a new Task instance"),
                    () -> assertEquals(TaskStatus.RUNNING, updated.getStatus()),
                    () -> assertEquals(TaskStatus.PENDING, original.getStatus(), "Original should remain PENDING"),
                    () -> assertEquals(original.getTaskId(), updated.getTaskId(), "TaskId should be preserved")
            );
        }

        @Test
        @DisplayName("withAssignedWorker returns new Task with workerId set")
        void test_WhenWithAssignedWorkerCalled_ThenNewTaskHasWorkerIdSet() {
            Task original = new Task.Builder()
                    .taskType("compute")
                    .build();

            Task updated = original.withAssignedWorker("worker-42");

            assertAll("withAssignedWorker copy",
                    () -> assertNotSame(original, updated),
                    () -> assertEquals("worker-42", updated.getAssignedWorkerId()),
                    () -> assertNull(original.getAssignedWorkerId(), "Original should still have null workerId"),
                    () -> assertEquals(original.getTaskId(), updated.getTaskId())
            );
        }

        @Test
        @DisplayName("withRetryCount returns new Task with retryCount updated")
        void test_WhenWithRetryCountCalled_ThenNewTaskHasUpdatedRetryCount() {
            Task original = new Task.Builder()
                    .taskType("compute")
                    .build();

            Task updated = original.withRetryCount(3);

            assertAll("withRetryCount copy",
                    () -> assertNotSame(original, updated),
                    () -> assertEquals(3, updated.getRetryCount()),
                    () -> assertEquals(0, original.getRetryCount(), "Original should still have retryCount 0"),
                    () -> assertEquals(original.getTaskId(), updated.getTaskId())
            );
        }
    }
}
