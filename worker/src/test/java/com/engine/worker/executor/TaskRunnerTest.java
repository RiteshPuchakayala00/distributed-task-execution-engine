package com.engine.worker.executor;

import com.engine.common.model.Task;
import com.engine.common.model.TaskResult;
import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import com.engine.worker.network.WorkerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRunnerTest {

    @Mock
    private WorkerClient client;

    @Test
    void testTaskExecutionAndResultSent() throws Exception {
        Task task = new Task.Builder().taskId("task-1").taskType("test").build();
        when(client.getAssignedWorkerId()).thenReturn("worker-1");

        TaskRunner runner = new TaskRunner(task, client);
        runner.run();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendMessage(captor.capture());

        Message msg = captor.getValue();
        assertEquals(MessageType.TASK_RESULT, msg.getType());
    }
}
