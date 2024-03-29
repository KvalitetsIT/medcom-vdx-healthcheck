package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.controller.model.HealthCheckModel;
import dk.medcom.healthcheck.service.HealthcheckService;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HtmlControllerTest {
    private HtmlController controller;
    private HealthcheckService healthcheckService;

    @BeforeEach
    public void setup() {
        healthcheckService = Mockito.mock(HealthcheckService.class);
        controller = new HtmlController(healthcheckService);
    }

    @Test
    public void testNullMessage() {
        var sts = new Status(true, null, 10L);
        var videoApi = new Status(true, null, 20L);
        var shortLink = new Status(true, null, 30L);
        var accessTokenForVideoApi = new Status(true, null, 40L);
        var smsStatus = new Status(true, null, 50L);
        var serviceResponse = new HealthcheckResult(sts, videoApi, shortLink, Optional.of(smsStatus), accessTokenForVideoApi, UUID.randomUUID());

        var healthCheckModel = new HealthCheckModel();
        healthCheckModel.setPhone("911");

        Mockito.when(healthcheckService.checkHealthWithProvisioningAndSms(healthCheckModel.getPhone())).thenReturn(serviceResponse);

        var result = controller.execute(healthCheckModel);
        assertNotNull(result);

        List<dk.medcom.healthcheck.controller.model.Status> statuList = (List<dk.medcom.healthcheck.controller.model.Status>) result.getModel().get("status");
        assertNotNull(statuList);

        assertEquals(6, statuList.size());
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Get token from STS", true,  10L, null), statuList.get(0));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create access token for VideoAPI", true, 40L, null), statuList.get(1));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create meeting in VideoAPI",  true,20L, null), statuList.get(2));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Access shortlink page", true,  30L, null), statuList.get(3));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Send SMS", true,  50L, null), statuList.get(4));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Total", true, 150L, null), statuList.get(5));
        assertEquals(serviceResponse.meetingUuid(), result.getModel().get("uuid"));
    }

    @Test
    public void testNullPhone() {
        var sts = new Status(true, null, 10L);
        var videoApi = new Status(true, null, 20L);
        var shortLink = new Status(true, null, 30L);
        var accessTokenForVideoApi = new Status(true, null, 40L);
        var serviceResponse = new HealthcheckResult(sts, videoApi, shortLink, Optional.empty(), accessTokenForVideoApi, UUID.randomUUID());

        Mockito.when(healthcheckService.checkHealthWithProvisioning()).thenReturn(serviceResponse);

        var result = controller.execute(new HealthCheckModel());
        assertNotNull(result);

        List<dk.medcom.healthcheck.controller.model.Status> statuList = (List<dk.medcom.healthcheck.controller.model.Status>) result.getModel().get("status");
        assertNotNull(statuList);

        assertEquals(5, statuList.size());
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Get token from STS", true,  10L, null), statuList.get(0));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create access token for VideoAPI", true, 40L, null), statuList.get(1));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create meeting in VideoAPI",  true,20L, null), statuList.get(2));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Access shortlink page", true,  30L, null), statuList.get(3));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Total", true, 100L, null), statuList.get(4));
        assertEquals(serviceResponse.meetingUuid(), result.getModel().get("uuid"));
    }

    @Test
    public void testEmptyPhone() {
        var sts = new Status(true, null, 10L);
        var videoApi = new Status(true, null, 20L);
        var shortLink = new Status(true, null, 30L);
        var accessTokenForVideoApi = new Status(true, null, 40L);
        var serviceResponse = new HealthcheckResult(sts, videoApi, shortLink, Optional.empty(), accessTokenForVideoApi, UUID.randomUUID());

        var healthCheckModel = new HealthCheckModel();
        healthCheckModel.setPhone("");

        Mockito.when(healthcheckService.checkHealthWithProvisioning()).thenReturn(serviceResponse);

        var result = controller.execute(healthCheckModel);
        assertNotNull(result);

        List<dk.medcom.healthcheck.controller.model.Status> statuList = (List<dk.medcom.healthcheck.controller.model.Status>) result.getModel().get("status");
        assertNotNull(statuList);

        assertEquals(5, statuList.size());
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Get token from STS", true,  10L, null), statuList.get(0));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create access token for VideoAPI", true, 40L, null), statuList.get(1));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create meeting in VideoAPI",  true,20L, null), statuList.get(2));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Access shortlink page", true,  30L, null), statuList.get(3));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Total", true, 100L, null), statuList.get(4));
        assertEquals(serviceResponse.meetingUuid(), result.getModel().get("uuid"));
    }

    @Test
    public void testSomeNullMessage() {
        var sts = new Status(true, null, 10L);
        var videoApi = new Status(true, null, 20L);
        var shortLink = new Status(false, "msg2", 30L);
        var accessTokenForVideoApi = new Status(false, "msg1", 40L);
        var serviceResponse = new HealthcheckResult(sts, videoApi, shortLink, Optional.empty(), accessTokenForVideoApi, UUID.randomUUID());

        Mockito.when(healthcheckService.checkHealthWithProvisioning()).thenReturn(serviceResponse);

        var result = controller.execute(null);
        assertNotNull(result);

        List<dk.medcom.healthcheck.controller.model.Status> statuList = (List<dk.medcom.healthcheck.controller.model.Status>) result.getModel().get("status");
        assertNotNull(statuList);

        assertEquals(5, statuList.size());
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Get token from STS", true,  10L, null), statuList.get(0));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create access token for VideoAPI", false, 40L, "msg1"), statuList.get(1));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Create meeting in VideoAPI",  true,20L, null), statuList.get(2));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Access shortlink page", false,  30L, "msg2"), statuList.get(3));
        assertEquals(new dk.medcom.healthcheck.controller.model.Status("Total", false, 100L, "msg1,msg2"), statuList.get(4));
    }
}
