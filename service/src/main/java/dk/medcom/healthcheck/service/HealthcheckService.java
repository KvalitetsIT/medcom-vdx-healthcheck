package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.MeetingStatus;
import dk.medcom.healthcheck.service.model.ProvisionStatus;

import java.util.UUID;

public interface HealthcheckService {
    HealthcheckResult checkHealth();

    HealthcheckResult checkHealthWithProvisioning();

    HealthcheckResult checkHealthWithProvisioningAndSms(String phone);

    MeetingStatus getStatus(UUID uuid);

    ProvisionStatus getProvisionStatus(UUID uuid);
}
