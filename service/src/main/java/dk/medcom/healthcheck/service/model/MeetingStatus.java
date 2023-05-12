package dk.medcom.healthcheck.service.model;

public record MeetingStatus(ProvisionStatus provisionStatus, SmsStatus smsStatus) {
}
