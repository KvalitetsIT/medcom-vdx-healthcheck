package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HealthcheckResult;

public interface HealthcheckServiceMetrics {
    HealthcheckResult checkHealth();
}
