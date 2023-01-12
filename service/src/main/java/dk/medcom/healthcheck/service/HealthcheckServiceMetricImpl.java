package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HealthcheckServiceMetricImpl implements HealthcheckService {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckServiceMetricImpl.class);
    private final HealthcheckService healthcheckService;
    private final MeterRegistry meterRegistry;

    private final int maxProvisionCheckTime = 120;

    public HealthcheckServiceMetricImpl(HealthcheckService healthcheckService, MeterRegistry meterRegistry) {
        this.healthcheckService = healthcheckService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public HealthcheckResult checkHealth() {
        logger.info("Running healthcheck for prometheus metrics.");

        var stsTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_STS);
        var videoApiTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_VIDEO_API);
        var shortLinkTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_SHORT_LINK);
        var accessTokenForVideoApi = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API);
        var provisionMeetingRoom = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.PROVISION_ROOM);

        var health = healthcheckService.checkHealth();

        int runTime = 0;
        ProvisionStatus provisionStatus = null;
        while(runTime < maxProvisionCheckTime) {
            provisionStatus = healthcheckService.getProvisionStatus(health.meetingUuid());

            if(provisionStatus.status().equals("PROVISIONED_OK")) {
                logger.debug("Provision status OK. Breaking loop.");
                break;
            }

            try {
                logger.debug("Sleeping - waiting to check provision status again.");
                runTime += 5;
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }

        logger.debug("Done checking for provision status.");

        recordTimeIfPositiveResponse(health.sts(), stsTimer);
        recordTimeIfPositiveResponse(health.videoAPi(), videoApiTimer);
        recordTimeIfPositiveResponse(health.shortLink(), shortLinkTimer);
        recordTimeIfPositiveResponse(health.accessTokenForVideoApi(), accessTokenForVideoApi);
        recordTimeIfProvisionedOk(provisionStatus, provisionMeetingRoom);

        return health;
    }

    private void recordTimeIfProvisionedOk(ProvisionStatus provisionStatus, Timer timer) {
        if(provisionStatus != null && provisionStatus.timeToProvision() > 0) {
            logger.debug("Recording metric in timer.");
            timer.record(provisionStatus.timeToProvision(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public HealthcheckResult checkHealthWithProvisioning() {
        return null;
    }

    @Override
    public ProvisionStatus getProvisionStatus(UUID uuid) {
        return null;
    }

    private void recordTimeIfPositiveResponse(Status status, Timer timer) {
        if(status.responseTime() > 0) {
            logger.debug("Recording metric in timer.");
            timer.record(status.responseTime(), TimeUnit.MILLISECONDS);
        }
    }
}
