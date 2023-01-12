package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.ProvisionStatus;

import java.util.UUID;

public interface HealthcheckService {
    HealthcheckResult checkHealth();

    HealthcheckResult checkHealthWithProvisioning();

    ProvisionStatus getProvisionStatus(UUID uuid);
}
