package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.service.HealthcheckService;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HealthcheckControllerTest {
    private HealthcheckController healthcheckController;
    private HealthcheckService healthcheckService;

    @BeforeEach
    public void setup() {
        healthcheckService = Mockito.mock(HealthcheckService.class);

        healthcheckController = new HealthcheckController(healthcheckService);
    }

    @Test
    public void testGet() {
        Status stsStatus = new Status(true, null, 10L);
        Status videoStatus = new Status(false, "another msg", 20L);
        Status shortLinkStatus = new Status(true, null, 30L);
        Status accessTokenStatus = new Status(false, "Some msg", 40L);
        var serviceResponse = new HealthcheckResult(stsStatus, videoStatus, shortLinkStatus, null, accessTokenStatus, UUID.randomUUID());

        Mockito.when(healthcheckService.checkHealth()).thenReturn(serviceResponse);

        var result = healthcheckController.v1HealthcheckGet();

        assertNotNull(result);
        var healthCheckResponse = result.getBody();
        assertNotNull(healthCheckResponse);
        assertNotNull(healthCheckResponse.getSts());
        assertNotNull(healthCheckResponse.getVideoApiAccessToken());
        assertNotNull(healthCheckResponse.getShortLink());
        assertNotNull(healthCheckResponse.getVideoApi());
        assertNull(healthCheckResponse.getSms());

        assertEquals(stsStatus.ok(), healthCheckResponse.getSts().getStatus());
        assertEquals(stsStatus.message(), healthCheckResponse.getSts().getMessage());
        assertEquals(stsStatus.responseTime(), healthCheckResponse.getSts().getResponseTime());

        assertEquals(videoStatus.ok(), healthCheckResponse.getVideoApi().getStatus());
        assertEquals(videoStatus.message(), healthCheckResponse.getVideoApi().getMessage());
        assertEquals(videoStatus.responseTime(), healthCheckResponse.getVideoApi().getResponseTime());

        assertEquals(shortLinkStatus.ok(), healthCheckResponse.getShortLink().getStatus());
        assertEquals(shortLinkStatus.message(), healthCheckResponse.getShortLink().getMessage());
        assertEquals(shortLinkStatus.responseTime(), healthCheckResponse.getShortLink().getResponseTime());

        assertEquals(accessTokenStatus.ok(), healthCheckResponse.getVideoApiAccessToken().getStatus());
        assertEquals(accessTokenStatus.message(), healthCheckResponse.getVideoApiAccessToken().getMessage());
        assertEquals(accessTokenStatus.responseTime(), healthCheckResponse.getVideoApiAccessToken().getResponseTime());
    }
}
