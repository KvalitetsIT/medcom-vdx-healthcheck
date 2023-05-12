package dk.medcom.healthcheck.service.model;

import java.util.Optional;
import java.util.UUID;

public record HealthcheckResult(Status sts, Status videoAPi, Status shortLink, Optional<Status> sms, Status accessTokenForVideoApi, UUID meetingUuid) {
}
