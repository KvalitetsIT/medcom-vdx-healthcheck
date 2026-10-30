package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.configuration.TimerConfiguration;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;
import dk.medcom.healthcheck.service.model.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HealthcheckServiceMetricImplUpGaugeTest {
    private HealthcheckService mockedHealthcheckService;
    private MeterRegistry meterRegistry;
    private HealthcheckServiceMetricImpl service;

    @BeforeEach
    public void setup() {
        mockedHealthcheckService = Mockito.mock(HealthcheckService.class);
        meterRegistry = new SimpleMeterRegistry();

        service = new HealthcheckServiceMetricImpl(mockedHealthcheckService, meterRegistry);
    }

    @Test
    public void testNoRunYet() {
        assertTrue(Double.isNaN(up(TimerConfiguration.SERVICE_STS)));
        assertTrue(Double.isNaN(up(TimerConfiguration.SERVICE_SHORT_LINK)));
        assertTrue(Double.isNaN(up(TimerConfiguration.PROVISION_ROOM)));
        assertEquals(0, lastRun());
    }

    @Test
    public void testAllOk() {
        var healthcheckResult = new HealthcheckResult(ok(), ok(), ok(), null, ok(), UUID.randomUUID());
        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(new ProvisionStatus("PROVISIONED_OK", 1000));

        var before = System.currentTimeMillis() / 1000;
        service.checkHealth();

        assertEquals(1, up(TimerConfiguration.SERVICE_STS));
        assertEquals(1, up(TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API));
        assertEquals(1, up(TimerConfiguration.SERVICE_VIDEO_API));
        assertEquals(1, up(TimerConfiguration.SERVICE_SHORT_LINK));
        assertEquals(1, up(TimerConfiguration.PROVISION_ROOM));
        assertTrue(lastRun() >= before);
    }

    @Test
    public void testShortLinkFails() {
        var healthcheckResult = new HealthcheckResult(ok(), ok(), failed("Wrong HTTP status from short link: 404"), null, ok(), UUID.randomUUID());
        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);
        Mockito.when(mockedHealthcheckService.getProvisionStatus(healthcheckResult.meetingUuid())).thenReturn(new ProvisionStatus("PROVISIONED_OK", 1000));

        service.checkHealth();

        assertEquals(1, up(TimerConfiguration.SERVICE_VIDEO_API));
        assertEquals(0, up(TimerConfiguration.SERVICE_SHORT_LINK));
        assertEquals(1, up(TimerConfiguration.PROVISION_ROOM));
    }

    @Test
    public void testStsFailsAndRestIsNotCalled() {
        var healthcheckResult = new HealthcheckResult(failed("STS down"), notCalled(), notCalled(), null, notCalled(), null);
        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);

        service.checkHealth();

        assertEquals(0, up(TimerConfiguration.SERVICE_STS));
        assertTrue(Double.isNaN(up(TimerConfiguration.SERVICE_ACCESS_TOKEN_FOR_VIDEO_API)));
        assertTrue(Double.isNaN(up(TimerConfiguration.SERVICE_VIDEO_API)));
        assertTrue(Double.isNaN(up(TimerConfiguration.SERVICE_SHORT_LINK)));
        assertTrue(Double.isNaN(up(TimerConfiguration.PROVISION_ROOM)));
    }

    @Test
    public void testPrometheusNames() {
        var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        var service = new HealthcheckServiceMetricImpl(mockedHealthcheckService, prometheusRegistry);
        var healthcheckResult = new HealthcheckResult(ok(), ok(), failed("Wrong HTTP status from short link: 404"), null, ok(), null);
        Mockito.when(mockedHealthcheckService.checkHealthWithProvisioning()).thenReturn(healthcheckResult);

        service.checkHealth();

        var scrape = prometheusRegistry.scrape();
        assertTrue(scrape.contains("vdx_healthcheck_up{service=\"shortLink\"} 0.0"), scrape);
        assertTrue(scrape.contains("vdx_healthcheck_up{service=\"videoAPi\"} 1.0"), scrape);
        assertTrue(scrape.contains("vdx_healthcheck_last_run_timestamp_seconds "), scrape);
    }

    private double up(String service) {
        return meterRegistry.get(HealthcheckServiceMetricImpl.UP_GAUGE_NAME).tag("service", service).gauge().value();
    }

    private double lastRun() {
        return meterRegistry.get(HealthcheckServiceMetricImpl.LAST_RUN_GAUGE_NAME).gauge().value();
    }

    private Status ok() {
        return new Status(true, null, 10L);
    }

    private Status failed(String message) {
        return new Status(false, message, 10L);
    }

    private Status notCalled() {
        return new Status(false, HealthcheckServiceImpl.NOT_CALLED_MESSAGE, 0L);
    }
}
