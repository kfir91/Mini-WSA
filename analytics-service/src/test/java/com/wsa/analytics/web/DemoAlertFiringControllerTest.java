package com.wsa.analytics.web;

import com.wsa.analytics.service.DemoAlertFiringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAlertFiringControllerTest {

    @Mock
    private DemoAlertFiringService demoAlertFiringService;

    @InjectMocks
    private DemoAlertFiringController controller;

    @Test
    void fireDemoAlertsDelegatesToService() {
        Map<String, Object> expected = Map.of("refreshed", 60);
        when(demoAlertFiringService.makeDemoFire()).thenReturn(expected);
        assertEquals(expected, controller.fireDemoAlerts());
        verify(demoAlertFiringService).makeDemoFire();
    }
}
