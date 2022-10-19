package dk.medcom.healthcheck.service.model;

public record HealthcheckResult(Status sts, Status videoAPi, Status shortLink, Status sms, Status accessTokenForVideoApi) {
}
