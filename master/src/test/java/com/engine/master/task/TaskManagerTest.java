package com.engine.master.task;

import com.engine.common.model.Task;
import com.engine.common.model.TaskPriority;
import com.engine.common.model.TaskResult;
import com.engine.common.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskManagerTest {

    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        taskManager = new TaskManager(100);
    }

    @Test
    void testSubmitTask() {
        Task task = new Task.Builder().taskId("task-1").taskType("test").priority(TaskPriority.HIGH).build();
        taskManager.submitTask(task);
        
        assertEquals(1, taskManager.getPendingCount());
        
        Optional<Task> retrieved = taskManager.getTask("task-1");
        assertTrue(retrieved.isPresent());
        assertEquals("task-1", retrieved.get().getTaskId());
    }

    @Test
    void testSubmitDuplicateTask() {
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        taskManager.submitTask(task);
        
        assertThrows(IllegalArgumentException.class, () -> taskManager.submitTask(task));
    }

    @Test
    void testPriorityOrdering() {
        Task low = new Task.Builder().taskId("low").taskType("test").priority(TaskPriority.LOW).build();
        Task high = new Task.Builder().taskId("high").taskType("test").priority(TaskPriority.HIGH).build();
        Task critical = new Task.Builder().taskId("critical").taskType("test").priority(TaskPriority.CRITICAL).build();
        
        taskManager.submitTask(low);
        taskManager.submitTask(critical);
        taskManager.submitTask(high);
        
        assertEquals(3, taskManager.getPendingCount());
        
        assertEquals("critical", taskManager.pollPendingTask().getTaskId());
        assertEquals("high", taskManager.pollPendingTask().getTaskId());
        assertEquals("low", taskManager.pollPendingTask().getTaskId());
        assertNull(taskManager.pollPendingTask());
    }

    @Test
    void testAssignTask() {
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        taskManager.submitTask(task);
        
        taskManager.assignTask("task-1", "worker-1", TaskStatus.ASSIGNED);
        
        Optional<Task> retrieved = taskManager.getTask("task-1");
        assertTrue(retrieved.isPresent());
        assertEquals("worker-1", retrieved.get().getAssignedWorkerId());
        assertEquals(TaskStatus.ASSIGNED, retrieved.get().getStatus());
    }

    @Test
    void testCompleteTask() {
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        taskManager.submitTask(task);
        
        TaskResult result = new TaskResult.Builder().taskId("task-1").workerId("w1").success(true).build();
        taskManager.completeTask(result);
        
        Optional<Task> retrieved = taskManager.getTask("task-1");
        assertTrue(retrieved.isPresent());
        assertEquals(TaskStatus.COMPLETED, retrieved.get().getStatus());
        
        assertTrue(taskManager.getTaskResult("task-1").isPresent());
    }

    @Test
    void testRequeueTask() {
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        taskManager.submitTask(task);
        Task polled = taskManager.pollPendingTask();
        
        taskManager.assignTask("task-1", "worker-1", TaskStatus.ASSIGNED);
        taskManager.requeueTask(polled);
        
        assertEquals(1, taskManager.getPendingCount());
        Optional<Task> retrieved = taskManager.getTask("task-1");
        assertTrue(retrieved.isPresent());
        assertNull(retrieved.get().getAssignedWorkerId());
        assertEquals(TaskStatus.PENDING, retrieved.get().getStatus());
    }
}
