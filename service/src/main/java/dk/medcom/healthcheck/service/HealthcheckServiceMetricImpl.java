//package dk.medcom.healthcheck.service;
//
//import dk.medcom.healthcheck.service.model.HealthcheckResult;
//import io.micrometer.core.instrument.Gauge;
//import io.micrometer.core.instrument.Timer;
//
//public class HealthcheckServiceMetricImpl implements HealthcheckService {
//    private final HealthcheckService healthcheckService;
//
//    public HealthcheckServiceMetricImpl(HealthcheckService healthcheckService) {
//        this.healthcheckService = healthcheckService;
//    }
//
//    @Override
//    public HealthcheckResult checkHealth() {
//        return healthcheckService.checkHealth();
//    }
//
//    private Number kuk() {
//    }
//}
