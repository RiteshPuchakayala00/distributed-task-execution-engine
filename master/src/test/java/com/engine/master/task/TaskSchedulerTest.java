package com.engine.master.task;

import com.engine.common.model.Task;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import com.engine.master.network.ConnectionManager;
import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.registry.WorkerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TaskSchedulerTest {

    @Mock
    private TaskManager taskManager;

    @Mock
    private WorkerRegistry workerRegistry;

    @Mock
    private ConnectionManager connectionManager;

    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaskScheduler(taskManager, workerRegistry, connectionManager);
    }

    @Test
    void testScheduleTaskToAvailableWorker() throws Exception {
        when(taskManager.getPendingCount()).thenReturn(1, 0);
        
        WorkerInfo worker = new WorkerInfo("w1", "host", 9090, 4);
        worker.setStatus(WorkerStatus.IDLE);
        when(workerRegistry.getAvailableWorkers()).thenReturn(List.of(worker));
        
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        when(taskManager.pollPendingTask()).thenReturn(task);

        scheduler.run();

        // Load should be incremented.
        assertEquals(1, worker.getCurrentLoad());
        
        // Since load (1) is less than available cores (4), compareAndSetStatus should not be called yet.
        verify(workerRegistry, never()).compareAndSetStatus("w1", WorkerStatus.IDLE, WorkerStatus.BUSY);
        verify(taskManager).assignTask(eq("task-1"), eq("w1"), any());
        
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(connectionManager).sendToWorker(eq("w1"), captor.capture());
        assertEquals(MessageType.TASK_ASSIGN, captor.getValue().getType());
    }

    @Test
    void testNoAvailableWorkers() throws Exception {
        when(taskManager.getPendingCount()).thenReturn(1);
        when(workerRegistry.getAvailableWorkers()).thenReturn(Collections.emptyList());

        scheduler.run();

        verify(taskManager, never()).pollPendingTask();
        verify(connectionManager, never()).sendToWorker(any(), any());
    }
}
