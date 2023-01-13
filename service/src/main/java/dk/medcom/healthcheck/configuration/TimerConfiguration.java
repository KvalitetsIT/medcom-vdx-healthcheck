package dk.medcom.healthcheck.configuration;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class TimerConfiguration {
    public static final String TIMER_NAME = "vdx_healthcheck";
    public static final String SERVICE_STS = "sts";
    public static final String SERVICE_VIDEO_API = "videoAPi";
    public static final String SERVICE_SHORT_LINK = "shortLink";
    public static final String SERVICE_ACCESS_TOKEN_FOR_VIDEO_API = "accessTokenForVideoApi";
    public static final String PROVISION_ROOM = "provisionRoom";

    @Bean
    public MeterBinder stsTimer() {
        return createTimer(TIMER_NAME, SERVICE_STS);
    }

    @Bean
    public MeterBinder videoApiTimer() {
        return createTimer(TIMER_NAME, SERVICE_VIDEO_API);
    }

    @Bean
    public MeterBinder shortLinkTimer() {
        return createTimer(TIMER_NAME, SERVICE_SHORT_LINK);
    }

    @Bean
    public MeterBinder accessTokenForVideoApiTimer() {
        return createTimer(TIMER_NAME, SERVICE_ACCESS_TOKEN_FOR_VIDEO_API);
    }

    @Bean
    public MeterBinder provisionTimer() {
        return createTimer(TIMER_NAME, PROVISION_ROOM);
    }

    private MeterBinder createTimer(String name, String service) {
        return registry -> Timer.builder(name)
                .distributionStatisticExpiry(Duration.ofMinutes(30L))
                .publishPercentiles(1, 0.95, 0.9, 0.8)
                .publishPercentileHistogram()
                .tag("service", service)
                .register(registry);
    }
}
