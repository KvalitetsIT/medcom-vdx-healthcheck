package dk.medcom.healthcheck.service.model;

public record Status(boolean ok, String message, long responseTime) {
}
