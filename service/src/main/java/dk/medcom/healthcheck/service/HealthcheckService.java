package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HealthcheckResult;

import java.util.UUID;

public interface HealthcheckService {
    HealthcheckResult checkHealth();

    HealthcheckResult checkHealthWithProvisioning();

    String getProvisionStatus(UUID uuid);
}
