package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.HelloServiceInput;

public interface HealthcheckService {
    HealthcheckResult checkHealth();
}
