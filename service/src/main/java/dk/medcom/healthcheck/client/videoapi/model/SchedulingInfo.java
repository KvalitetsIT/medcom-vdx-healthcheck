package dk.medcom.healthcheck.client.videoapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record SchedulingInfo(String provisionStatus,
                             @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss Z")
                             OffsetDateTime createdTime,
                             @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss Z")
                             OffsetDateTime provisionTimestamp) {
}
