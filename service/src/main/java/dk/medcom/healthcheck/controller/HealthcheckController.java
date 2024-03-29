package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.service.HealthcheckService;
import org.openapitools.api.HealthcheckApi;
import org.openapitools.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class HealthcheckController implements HealthcheckApi {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckController.class);
    private final HealthcheckService healthcheckService;

    public HealthcheckController(HealthcheckService healthcheckService) {
        this.healthcheckService = healthcheckService;
    }

    @Override
    public ResponseEntity<HealthcheckResponse> v1HealthcheckGet() {
        logger.info("Doing health check");

        var serviceResponse = healthcheckService.checkHealth();

        var healthcheckResponse = new org.openapitools.model.HealthcheckResponse();

        var videoApiStatus = createStatus(serviceResponse.videoAPi());
        var shortLinkStatus = createStatus(serviceResponse.shortLink());
        var stsStatus = createStatus(serviceResponse.sts());
        var accessTokenForVideoApi = createStatus(serviceResponse.accessTokenForVideoApi());

        healthcheckResponse.setShortLink(shortLinkStatus);
        healthcheckResponse.setSts(stsStatus);
        healthcheckResponse.setVideoApi(videoApiStatus);
        healthcheckResponse.setVideoApiAccessToken(accessTokenForVideoApi);

        return ResponseEntity.ok(healthcheckResponse);
    }

    @Override
    public ResponseEntity<MeetingStatus> v1StatusUuidGet(UUID uuid) {
        logger.debug("Getting provision status for {}.", uuid);

        var result = healthcheckService.getStatus(uuid);

        var provisionStatus = result.provisionStatus();
        var smsStatus = result.smsStatus();

        return ResponseEntity.ok(new MeetingStatus()
                .schedulingInfo(new SchedulingInfo(provisionStatus.status(), provisionStatus.timeToProvision()))
                .smsInfo(new SmsInfo(smsStatus.status())));
    }

    private Status createStatus(dk.medcom.healthcheck.service.model.Status serviceStatus) {
        var status = new Status();
        status.setStatus(serviceStatus.ok());
        status.setMessage(serviceStatus.message());
        status.setResponseTime(serviceStatus.responseTime());

        return status;
    }
}
