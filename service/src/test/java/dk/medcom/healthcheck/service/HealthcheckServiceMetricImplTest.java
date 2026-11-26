package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;

public class HealthcheckServiceMetricImplTest {
    private HealthcheckService mockedHealthcheckService;
    private MeterRegistry meterRegistry;
    private HealthcheckServiceMetricImpl service;
    private Timer keycloakClientCredentialsTimer;
    private Timer keycloakUserTimer;

    @BeforeEach
    public void setup() {
        mockedHealthcheckService = Mockito.mock(HealthcheckService.class);
        meterRegistry = Mockito.mock(MeterRegistry.class);

        keycloakClientCredentialsTimer = Mockito.mock(Timer.class);
        keycloakUserTimer = Mockito.mock(Timer.class);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_KEYCLOAK_CLIENT_CREDENTIALS)).thenReturn(keycloakClientCredentialsTimer);
        Mockito.when(meterRegistry.timer(TimerConfiguration.TIMER_NAME, "service", TimerConfiguration.SERVICE_KEYCLOAK_USER)).thenReturn(keycloakUserTimer);

        service = new HealthcheckServiceMetricImpl(mockedHealthcheckService, meterRegistry);
    }
    @Test
    public void testMetricsCollected() {
        var sts = createStatus(10L);
        var videoApi = createStatus(20L);
        var shortLink = createStatus(30L);
        var accessTokenForVideoApi = createStatus(40L);
        var provisionStatus = new ProvisionStatus("PROVISIONED_OK", 1000);
        HealthcheckResult healthcheckResult = new HealthcheckResult(sts, videoApi, shortLink, null, accessTokenForVideoApi, UUID.randomUUID(), createStatus(50L), createStatus(60L));

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

        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(provisionStatus);
        service.checkHealth();

        Mockito.verify(mockedHealthcheckService, times(1)).checkHealthWithProvisioning();
        Mockito.verify(mockedHealthcheckService, times(1)).getProvisionStatus(healthcheckResult.meetingUuid());
        Mockito.verify(stsTimer, times(1)).record(sts.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(videoApiTimer, times(1)).record(videoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(shortLinkTimer, times(1)).record(shortLink.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(accessTokenForVideoApiTimer, times(1)).record(accessTokenForVideoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(provisionRoomTimer, times(1)).record(provisionStatus.timeToProvision(), TimeUnit.MILLISECONDS);
        Mockito.verify(keycloakClientCredentialsTimer, times(1)).record(50L, TimeUnit.MILLISECONDS);
        Mockito.verify(keycloakUserTimer, times(1)).record(60L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testMetricsCollectedShortLink0ResponseTime() {
        var sts = createStatus(10L);
        var videoApi = createStatus(20L);
        var shortLink = createStatus(0);
        var accessTokenForVideoApi = createStatus(40L);
        var provisionStatus = new ProvisionStatus("PROVISIONED_OK", 1000);
        HealthcheckResult healthcheckResult = new HealthcheckResult(sts, videoApi, shortLink, null, accessTokenForVideoApi, UUID.randomUUID(), createStatus(50L), createStatus(60L));

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

        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(provisionStatus);
        service.checkHealth();

        Mockito.verify(mockedHealthcheckService, times(1)).checkHealthWithProvisioning();
        Mockito.verify(mockedHealthcheckService, times(1)).getProvisionStatus(healthcheckResult.meetingUuid());
        Mockito.verify(stsTimer, times(1)).record(sts.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(videoApiTimer, times(1)).record(videoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verifyNoInteractions(shortLinkTimer);
        Mockito.verify(accessTokenForVideoApiTimer, times(1)).record(accessTokenForVideoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(provisionRoomTimer, times(1)).record(provisionStatus.timeToProvision(), TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCheckHealthWhenNoMeetingUuid() {
        var sts = createStatus(10L);
        var videoApi = createStatus(20L);
        var shortLink = createStatus(30L);
        var accessTokenForVideoApi = createStatus(40L);
        var provisionStatus = new ProvisionStatus("PROVISIONED_OK", 1000);
        HealthcheckResult healthcheckResult = new HealthcheckResult(sts, videoApi, shortLink, null, accessTokenForVideoApi, null, createStatus(50L), createStatus(60L));

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

        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(provisionStatus);
        service.checkHealth();

        Mockito.verify(mockedHealthcheckService, times(1)).checkHealthWithProvisioning();
        Mockito.verifyNoMoreInteractions(mockedHealthcheckService);
        Mockito.verify(stsTimer, times(1)).record(sts.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(videoApiTimer, times(1)).record(videoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(shortLinkTimer, times(1)).record(shortLink.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verify(accessTokenForVideoApiTimer, times(1)).record(accessTokenForVideoApi.responseTime(), TimeUnit.MILLISECONDS);
        Mockito.verifyNoInteractions(provisionRoomTimer);
    }

    @Test
    public void testKeycloakMetricsCollected() {
        var registry = new SimpleMeterRegistry();
        service = new HealthcheckServiceMetricImpl(mockedHealthcheckService, registry);
        var ok = createStatus(10L);
        var keycloakUser = new Status(false, "Wrong HTTP status from Keycloak: 401", 60L);
        var healthcheckResult = new HealthcheckResult(ok, ok, ok, null, ok, null, createStatus(50L), keycloakUser);
        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);

        service.checkHealth();

        var clientCredentialsTimer = registry.get(TimerConfiguration.TIMER_NAME).tag("service", TimerConfiguration.SERVICE_KEYCLOAK_CLIENT_CREDENTIALS).timer();
        var userTimer = registry.get(TimerConfiguration.TIMER_NAME).tag("service", TimerConfiguration.SERVICE_KEYCLOAK_USER).timer();
        assertEquals(1, clientCredentialsTimer.count());
        assertEquals(50d, clientCredentialsTimer.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(1, userTimer.count());
        assertEquals(60d, userTimer.totalTime(TimeUnit.MILLISECONDS));
    }


    private Status createStatus(long responseTime) {
        return new Status(true, null, responseTime);
    }
}
