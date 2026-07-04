package com.engine.worker.network;

import com.engine.common.protocol.Message;
import com.engine.common.protocol.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatTask Tests")
class HeartbeatTaskTest {

    @Mock
    private WorkerClient client;

    @InjectMocks
    private HeartbeatTask heartbeatTask;

    @Test
    @DisplayName("Does not send message when client is inactive")
    void test_WhenClientInactive_ThenDoesNotSendMessage() throws Exception {
        when(client.isActive()).thenReturn(false);

        heartbeatTask.run();

        verify(client, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Does not send message when assigned worker ID is null")
    void test_WhenIdNull_ThenDoesNotSendMessage() throws Exception {
        when(client.isActive()).thenReturn(true);
        when(client.getAssignedWorkerId()).thenReturn(null);

        heartbeatTask.run();

        verify(client, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Sends heartbeat when active and has ID")
    void test_WhenActiveAndHasId_ThenSendsHeartbeat() throws Exception {
        when(client.isActive()).thenReturn(true);
        when(client.getAssignedWorkerId()).thenReturn("worker-1");

        heartbeatTask.run();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendMessage(captor.capture());

        Message capturedMessage = captor.getValue();
        assertEquals(MessageType.HEARTBEAT, capturedMessage.getType());
    }
}
