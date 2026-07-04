package com.engine.master.metrics;

import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private TaskManager taskManager;

    @Mock
    private WorkerRegistry registry;

    @Test
    void testMetricsCalculation() {
        MetricsService service = new MetricsService(taskManager, registry);
        
        service.recordTaskSubmitted();
        service.recordTaskSubmitted();
        service.recordTaskSubmitted();
        
        service.recordTaskCompleted(100);
        service.recordTaskCompleted(200);
        
        service.recordTaskFailed();
        
        WorkerInfo w1 = new WorkerInfo("w1", "host", 9090, 4);
        WorkerInfo w2 = new WorkerInfo("w2", "host", 9090, 4);
        
        when(registry.getAvailableWorkers()).thenReturn(List.of(w1, w2));
        when(taskManager.getPendingCount()).thenReturn(5);
        
        MetricsSnapshot snapshot = service.getSnapshot();
        
        assertEquals(3, snapshot.totalTasksSubmitted());
        assertEquals(2, snapshot.totalTasksCompleted());
        assertEquals(1, snapshot.totalTasksFailed());
        assertEquals(5, snapshot.currentPendingTasks());
        assertEquals(2, snapshot.activeWorkers());
        assertEquals(150.0, snapshot.averageLatencyMs(), 0.01);
    }
}
