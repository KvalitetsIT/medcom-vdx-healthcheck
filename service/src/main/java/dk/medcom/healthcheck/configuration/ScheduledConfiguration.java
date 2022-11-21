package dk.medcom.healthcheck.configuration;

import dk.medcom.healthcheck.service.HealthcheckService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@Configuration
public class ScheduledConfiguration {
    @Autowired
    private HealthcheckService healthcheckServiceMetricImpl;

    @Autowired
    private MeterRegistry meterRegistry;

    @Scheduled(fixedRateString = "PT5M")
    public void executeHealthCheck() {
        healthcheckServiceMetricImpl.checkHealth();
    }
}
