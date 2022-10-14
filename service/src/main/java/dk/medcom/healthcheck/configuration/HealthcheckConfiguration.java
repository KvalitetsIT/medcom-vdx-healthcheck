package dk.medcom.healthcheck.configuration;

import dk.medcom.healthcheck.service.HelloService;
import dk.medcom.healthcheck.service.HelloServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthcheckConfiguration {
    @Bean
    public HelloService helloService() {
        return new HelloServiceImpl();
    }
}
