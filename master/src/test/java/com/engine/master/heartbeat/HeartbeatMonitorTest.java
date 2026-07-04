package com.engine.master.heartbeat;

import com.engine.master.registry.WorkerInfo;
import com.engine.master.registry.WorkerRegistry;
import com.engine.master.registry.WorkerStatus;
import com.engine.master.recovery.FailureRecoveryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private WorkerRegistry registry;

    @Mock
    private FailureRecoveryManager recoveryManager;

    private HeartbeatMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new HeartbeatMonitor(registry, recoveryManager, 9000, 15000);
    }

    @Test
    void testWorkerMarkedUnresponsive() throws Exception {
        WorkerInfo worker = mock(WorkerInfo.class);
        when(worker.getWorkerId()).thenReturn("w1");
        when(worker.getLastHeartbeatMs()).thenReturn(System.currentTimeMillis() - 10000); // 10s ago
        when(worker.getStatus()).thenReturn(WorkerStatus.IDLE);
        
        when(registry.getAllWorkers()).thenReturn(List.of(worker));
        
        monitor.run();
        
        verify(registry).updateStatus("w1", WorkerStatus.UNRESPONSIVE);
        verify(recoveryManager, never()).handleWorkerFailure(anyString());
    }

    @Test
    void testWorkerMarkedDead() throws Exception {
        WorkerInfo worker = mock(WorkerInfo.class);
        when(worker.getWorkerId()).thenReturn("w1");
        when(worker.getLastHeartbeatMs()).thenReturn(System.currentTimeMillis() - 16000); // 16s ago
        when(worker.getStatus()).thenReturn(WorkerStatus.UNRESPONSIVE);
        
        when(registry.getAllWorkers()).thenReturn(List.of(worker));
        
        monitor.run();
        
        verify(registry).updateStatus("w1", WorkerStatus.DEAD);
        verify(recoveryManager).handleWorkerFailure("w1");
    }

    @Test
    void testWorkerRecovers() throws Exception {
        WorkerInfo worker = mock(WorkerInfo.class);
        when(worker.getWorkerId()).thenReturn("w1");
        when(worker.getLastHeartbeatMs()).thenReturn(System.currentTimeMillis() - 1000); // 1s ago
        when(worker.getStatus()).thenReturn(WorkerStatus.UNRESPONSIVE);
        
        when(registry.getAllWorkers()).thenReturn(List.of(worker));
        
        monitor.run();
        
        verify(registry).updateStatus("w1", WorkerStatus.IDLE);
        verify(recoveryManager, never()).handleWorkerFailure(anyString());
    }
}
