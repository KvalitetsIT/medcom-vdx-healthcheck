package dk.medcom.healthcheck.client.videoapi;

import dk.medcom.healthcheck.client.videoapi.model.CreateMeeting;
import dk.medcom.healthcheck.client.videoapi.model.Meeting;
import dk.medcom.healthcheck.client.videoapi.model.PatchMeeting;
import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.videoapi.model.SchedulingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;
import java.util.UUID;

public class VideoApiClientImpl implements VideoApiClient {
    private static final Logger logger = LoggerFactory.getLogger(VideoApiClientImpl.class);
    private final WebClient webClient;

    public VideoApiClientImpl(WebClient.Builder webClientBuilder, String endpoint) {
        logger.info("Video API endpoint: " + endpoint);
        webClient = webClientBuilder
                .baseUrl(endpoint)
                .build();
    }

    @Override
    public Result<Meeting> createMeeting(String accessToken, CreateMeeting createMeeting) {
        long start = System.currentTimeMillis();
        try {
            var createMeetingResult = webClient.post()
                    .uri("/meetings/")
                    .header("Authorization", "Holder-of-key " + accessToken)
                    .bodyValue(createMeeting)
                    .retrieve()
                    .bodyToMono(Meeting.class)
                    .block();

            return new Result<>(System.currentTimeMillis()-start, createMeetingResult);
        }
        catch(Exception e) {
            logger.error("Error calling Video API.", e);
            if(e instanceof WebClientResponseException we) {
                logger.error(we.getResponseBodyAsString());
            }
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }

    @Override
    public Result<Meeting> closeMeeting(String accessToken, UUID createMeeting) {
        long start = System.currentTimeMillis();
        try {
            var patchMeeting = new PatchMeeting();
            patchMeeting.setEndTime(OffsetDateTime.now());
            var createMeetingResult = webClient.patch()
                    .uri("/meetings/{uuid}", createMeeting)
                    .header("Authorization", "Holder-of-key " + accessToken)
                    .bodyValue(patchMeeting)
                    .retrieve()
                    .bodyToMono(Meeting.class);

            return new Result<>(System.currentTimeMillis()-start, createMeetingResult.block());
        }
        catch(Exception e) {
            logger.error("Error calling Video API.", e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }

    @Override
    public Result<SchedulingInfo> readSchedulingInfo(String accessToken, UUID meetingUuid) {
        long start = System.currentTimeMillis();
        try {
            var schedulingInfo = webClient.get()
                    .uri("/scheduling-info/{uuid}", meetingUuid)
                    .header("Authorization", "Holder-of-key " + accessToken)
                    .retrieve()
                    .bodyToMono(SchedulingInfo.class)
                    .block();

            logger.debug(schedulingInfo.toString());
            return new Result(System.currentTimeMillis()-start, schedulingInfo);
        }
        catch(Exception e) {
            logger.error("Error reading scheduling info from VideoAPI.", e);
            return new Result(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }
}
