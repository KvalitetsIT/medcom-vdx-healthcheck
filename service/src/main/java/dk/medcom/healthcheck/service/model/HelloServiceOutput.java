package dk.medcom.healthcheck.service.model;

import java.time.ZonedDateTime;

public record HelloServiceOutput(String name, ZonedDateTime now) {
}
