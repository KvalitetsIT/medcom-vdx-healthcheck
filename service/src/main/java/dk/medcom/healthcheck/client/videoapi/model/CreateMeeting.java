package dk.medcom.healthcheck.client.videoapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

public class CreateMeeting {
    @NotNull
    private String subject;
    @NotNull
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ssZZZ")
    private OffsetDateTime startTime;
    @NotNull
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ssZZZ")
    private OffsetDateTime endTime;
    private String description;
    private String projectCode;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }
}
