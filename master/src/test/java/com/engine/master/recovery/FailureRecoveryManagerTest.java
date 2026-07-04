package com.engine.master.recovery;

import com.engine.common.model.Task;
import com.engine.master.network.ConnectionManager;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailureRecoveryManagerTest {

    @Mock
    private TaskManager taskManager;

    @Mock
    private WorkerRegistry registry;

    @Mock
    private ConnectionManager connectionManager;

    @Test
    void testHandleWorkerFailure() {
        FailureRecoveryManager recoveryManager = new FailureRecoveryManager(taskManager, registry, connectionManager);
        
        Task task1 = new Task.Builder().taskId("t1").taskType("test").assignedWorkerId("w1").build();
        Task task2 = new Task.Builder().taskId("t2").taskType("test").assignedWorkerId("w1").build();
        
        when(taskManager.getTasksAssignedToWorker("w1")).thenReturn(List.of(task1, task2));
        
        recoveryManager.handleWorkerFailure("w1");
        
        verify(taskManager).requeueTask(task1);
        verify(taskManager).requeueTask(task2);
        
        verify(connectionManager).removeConnection("w1");
        verify(registry).deregisterWorker("w1");
    }
}
