package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class HealthcheckServiceMetricImpl implements HealthcheckServiceMetrics {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckServiceMetricImpl.class);
    public static final String UP_GAUGE_NAME = "vdx_healthcheck_up";
    public static final String LAST_RUN_GAUGE_NAME = "vdx_healthcheck_last_run_timestamp_seconds";
    private static final List<String> SERVICES = List.of(TimerConfiguration.SERVICE_STS,
            TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API,
            TimerConfiguration.SERVICE_VIDEO_API,
            TimerConfiguration.SERVICE_SHORT_LINK,
            TimerConfiguration.PROVISION_ROOM);

    private final HealthcheckService healthcheckService;
    private final MeterRegistry meterRegistry;

    private final int maxProvisionCheckTime = 120;

    // 1 = OK, 0 = failed, NaN = not called / unknown
    private final Map<String, Double> upStatus = new ConcurrentHashMap<>();

    private final AtomicLong lastRun = new AtomicLong();

    public HealthcheckServiceMetricImpl(HealthcheckService healthcheckService, MeterRegistry meterRegistry) {
        this.healthcheckService = healthcheckService;
        this.meterRegistry = meterRegistry;

        SERVICES.forEach(service -> {
            upStatus.put(service, Double.NaN);
            Gauge.builder(UP_GAUGE_NAME, upStatus, x -> x.getOrDefault(service, Double.NaN))
                    .tag("service", service)
                    .strongReference(true)
                    .register(meterRegistry);
        });

        Gauge.builder(LAST_RUN_GAUGE_NAME, lastRun, AtomicLong::doubleValue)
                .strongReference(true)
                .register(meterRegistry);
    }

    @Override
    public HealthcheckResult checkHealth() {
        logger.info("Running healthcheck for prometheus metrics.");

        var stsTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_STS);
        var videoApiTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_VIDEO_API);
        var shortLinkTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_SHORT_LINK);
        var accessTokenForVideoApi = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API);
        var provisionMeetingRoom = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.PROVISION_ROOM);

        var health = healthcheckService.checkHealthWithProvisioning();

        var provisionStatus = checkProvisionStatus(health.meetingUuid());

        recordTimeIfPositiveResponse(health.sts(), stsTimer, TimerConfiguration.SERVICE_STS);
        recordTimeIfPositiveResponse(health.videoAPi(), videoApiTimer, TimerConfiguration.SERVICE_VIDEO_API);
        recordTimeIfPositiveResponse(health.shortLink(), shortLinkTimer, TimerConfiguration.SERVICE_SHORT_LINK);
        recordTimeIfPositiveResponse(health.accessTokenForVideoApi(), accessTokenForVideoApi, TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API);
        recordTimeIfProvisionedOk(provisionStatus, provisionMeetingRoom, TimerConfiguration.PROVISION_ROOM);

        setUpStatus(TimerConfiguration.SERVICE_STS, health.sts());
        setUpStatus(TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API, health.accessTokenForVideoApi());
        setUpStatus(TimerConfiguration.SERVICE_VIDEO_API, health.videoAPi());
        setUpStatus(TimerConfiguration.SERVICE_SHORT_LINK, health.shortLink());
        upStatus.put(TimerConfiguration.PROVISION_ROOM, provisionStatus == null ? Double.NaN : provisionStatus.status().equals("PROVISIONED_OK") ? 1d : 0d);
        lastRun.set(System.currentTimeMillis() / 1000);

        return health;
    }

    private void setUpStatus(String service, Status status) {
        if(HealthcheckServiceImpl.NOT_CALLED_MESSAGE.equals(status.message())) {
            upStatus.put(service, Double.NaN);
            return;
        }

        if(!status.ok()) {
            logger.warn("Healthcheck failed for {}: {}", service, status.message());
        }
        upStatus.put(service, status.ok() ? 1d : 0d);
    }

    private ProvisionStatus checkProvisionStatus(UUID meetingUuid) {
        if (meetingUuid == null) {
            logger.debug("Meeting uuid not found. Skipping provision status check.");
            return null;
        }

        logger.debug("Checking provision status.");
        int runTime = 0;
        ProvisionStatus provisionStatus = null;

        while (runTime < maxProvisionCheckTime) {
            provisionStatus = healthcheckService.getProvisionStatus(meetingUuid);

            if (provisionStatus.status().equals("PROVISIONED_OK")) {
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

        return provisionStatus;
    }

    private void recordTimeIfProvisionedOk(ProvisionStatus provisionStatus, Timer timer, String timerName) {
        if(provisionStatus != null && provisionStatus.timeToProvision() > 0) {
            logger.debug("Recording metric in timer. Timer: {}. Response time: {}", timerName, provisionStatus.timeToProvision());
            timer.record(provisionStatus.timeToProvision(), TimeUnit.MILLISECONDS);
        }
    }

    private void recordTimeIfPositiveResponse(Status status, Timer timer, String timerName) {
        if(status.responseTime() > 0) {
            logger.debug("Recording metric in timer. Timer: {}. Response time: {}", timerName, status.responseTime());
            timer.record(status.responseTime(), TimeUnit.MILLISECONDS);
        }
    }
}