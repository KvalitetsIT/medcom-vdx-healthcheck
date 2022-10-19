package dk.medcom.healthcheck.client.videoapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public class PatchMeeting {
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ssZZZ")
    private OffsetDateTime endTime;

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }
}
