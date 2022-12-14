package dk.medcom.healthcheck.client.videoapi;

import dk.medcom.healthcheck.client.videoapi.model.CreateMeeting;
import dk.medcom.healthcheck.client.videoapi.model.Meeting;
import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.videoapi.model.SchedulingInfo;

import java.util.UUID;

public interface VideoApiClient {
    Result<Meeting> createMeeting(String accessToken, CreateMeeting createMeeting);

    Result<Meeting> closeMeeting(String accessToken, UUID meetingUuid);

    Result<SchedulingInfo> readSchedulingInfo(String accessToken, UUID meetingUuid);
}
