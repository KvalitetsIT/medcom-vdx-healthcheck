package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

public class HealthcheckServiceMetricImpl implements HealthcheckService {
    private final HealthcheckService healthcheckService;
    private final MeterRegistry meterRegistry;

    public HealthcheckServiceMetricImpl(HealthcheckService healthcheckService, MeterRegistry meterRegistry) {
        this.healthcheckService = healthcheckService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public HealthcheckResult checkHealth() {
        var stsTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_STS);
        var videoApiTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_VIDEO_API);
        var shortLinkTimer = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_SHORT_LINK);
        var accessTokenForVideoApi = meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API);

        var health = healthcheckService.checkHealth();

        recordTimeIfPositiveResponse(health.sts(), stsTimer);
        recordTimeIfPositiveResponse(health.videoAPi(), videoApiTimer);
        recordTimeIfPositiveResponse(health.shortLink(), shortLinkTimer);
        recordTimeIfPositiveResponse(health.accessTokenForVideoApi(), accessTokenForVideoApi);

        return health;
    }

    private void recordTimeIfPositiveResponse(Status status, Timer timer) {
        if(status.responseTime() > 0) {
            timer.record(status.responseTime(), TimeUnit.MILLISECONDS);
        }
    }
}
