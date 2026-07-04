package com.engine.worker.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconnectHandlerTest {

    @Mock
    private WorkerClient client;

    @Test
    void testReconnectSuccessOnFirstAttempt() throws Exception {
        ReconnectHandler handler = new ReconnectHandler(client, 3, 10);
        
        boolean result = handler.attemptReconnect();
        
        assertTrue(result);
        verify(client, times(1)).connectAndRegister();
    }

    @Test
    void testReconnectSuccessOnSecondAttempt() throws Exception {
        ReconnectHandler handler = new ReconnectHandler(client, 3, 10);
        
        doThrow(new IOException("Connection refused")).doNothing().when(client).connectAndRegister();
        
        boolean result = handler.attemptReconnect();
        
        assertTrue(result);
        verify(client, times(2)).connectAndRegister();
    }

    @Test
    void testReconnectFailsAfterMaxAttempts() throws Exception {
        ReconnectHandler handler = new ReconnectHandler(client, 3, 10);
        
        doThrow(new IOException("Connection refused")).when(client).connectAndRegister();
        
        boolean result = handler.attemptReconnect();
        
        assertFalse(result);
        verify(client, times(3)).connectAndRegister();
    }
}
