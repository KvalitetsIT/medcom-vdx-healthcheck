package dk.medcom.healthcheck.controller.model;

public record Status(String name, boolean ok, long responseTime, String message) {
}
