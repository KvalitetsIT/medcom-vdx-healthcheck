package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.times;

public class HealthcheckServiceMetricImplTest {
    private HealthcheckService mockedHealthcheckService;
    private MeterRegistry meterRegistry;
    private HealthcheckServiceMetricImpl service;

    @BeforeEach
    public void setup() {
        mockedHealthcheckService = Mockito.mock(HealthcheckService.class);
        meterRegistry = Mockito.mock(MeterRegistry.class);

        service = new HealthcheckServiceMetricImpl(mockedHealthcheckService, meterRegistry);
    }
    @Test
    public void testMetricsCollected() {
        var sts = createStatus(10L);
        var videoApi = createStatus(20L);
        var shortLink = createStatus(30L);
        var accessTokenForVideoApi = createStatus(40L);
        var provisionStatus = new ProvisionStatus("PROVISIONED_OK", 1000);
        HealthcheckResult healthcheckResult = new HealthcheckResult(sts, videoApi, shortLink, null, accessTokenForVideoApi, UUID.randomUUID());

        var stsTimer = Mockito.mock(Timer.class);
        var videoApiTimer = Mockito.mock(Timer.class);
        var shortLinkTimer = Mockito.mock(Timer.class);
        var accessTokenForVideoApiTimer = Mockito.mock(Timer.class);
        var provisionRoomTimer = Mockito.mock(Timer.class);

        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_STS)).thenReturn(stsTimer);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_VIDEO_API)).thenReturn(videoApiTimer);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_SHORT_LINK)).thenReturn(shortLinkTimer);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API)).thenReturn(accessTokenForVideoApiTimer);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.PROVISION_ROOM)).thenReturn(provisionRoomTimer);

        Mockito.when(mockedHealthcheckService.checkHealth()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(provisionStatus);
        service.checkHealth();

        Mockito.verify(mockedHealthcheckService, times(1)).checkHealth();
        Mockito.verify(stsTimer, times(1)).record(sts.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(videoApiTimer, times(1)).record(videoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(shortLinkTimer, times(1)).record(shortLink.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(accessTokenForVideoApiTimer, times(1)).record(accessTokenForVideoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(provisionRoomTimer, times(1)).record(provisionStatus.timeToProvision(), TimeUnit.MILLISECONDS);
    }

    private Status createStatus(long responseTime) {
        return new Status(true, null, responseTime);
    }
}
