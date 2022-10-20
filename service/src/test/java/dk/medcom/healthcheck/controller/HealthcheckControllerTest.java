package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.service.HealthcheckService;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HealthcheckControllerTest {
    private HealthcheckController healthcheckController;
    private HealthcheckService healthcheckService;

    @Before
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
        var serviceResponse = new HealthcheckResult(stsStatus, videoStatus, shortLinkStatus, null, accessTokenStatus);

        assertNotNull(serviceResponse);
        assertNotNull(serviceResponse.sts());
        assertNotNull(serviceResponse.accessTokenForVideoApi());
        assertNotNull(serviceResponse.shortLink());
        assertNotNull(serviceResponse.videoAPi());
        assertNull(serviceResponse.sms());

        assertEquals(stsStatus.ok(), serviceResponse.sts().ok());
        assertEquals(stsStatus.message(), serviceResponse.sts().message());
        assertEquals(stsStatus.responseTime(), serviceResponse.sts().responseTime());

        assertEquals(videoStatus.ok(), serviceResponse.videoAPi().ok());
        assertEquals(videoStatus.message(), serviceResponse.videoAPi().message());
        assertEquals(videoStatus.responseTime(), serviceResponse.videoAPi().responseTime());

        assertEquals(shortLinkStatus.ok(), serviceResponse.shortLink().ok());
        assertEquals(shortLinkStatus.message(), serviceResponse.shortLink().message());
        assertEquals(shortLinkStatus.responseTime(), serviceResponse.shortLink().responseTime());

        assertEquals(accessTokenStatus.ok(), serviceResponse.accessTokenForVideoApi().ok());
        assertEquals(accessTokenStatus.message(), serviceResponse.accessTokenForVideoApi().message());
        assertEquals(accessTokenStatus.responseTime(), serviceResponse.accessTokenForVideoApi().responseTime());
    }
}
