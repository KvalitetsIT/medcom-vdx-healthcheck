package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.client.security.AuthorizationClient;
import dk.medcom.healthcheck.client.security.StsClient;
import dk.medcom.healthcheck.client.security.TokenEncoder;
import dk.medcom.healthcheck.client.security.model.AccessToken;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import dk.medcom.healthcheck.service.model.Status;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClient;
import dk.medcom.healthcheck.client.videoapi.VideoApiClient;
import dk.medcom.healthcheck.client.videoapi.model.CreateMeeting;
import dk.medcom.healthcheck.client.Result;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

public class HealthcheckServiceImpl implements HealthcheckService {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckServiceImpl.class);
    private final StsClient stsClient;
    private final ShortLinkClient shortLinkClient;
    private final AuthorizationClient authorizationClient;
    private final VideoApiClient videoApiClient;
    private final TokenEncoder tokenEncoder;

    public HealthcheckServiceImpl(StsClient stsClient, ShortLinkClient shortLinkClient, AuthorizationClient authorizationClient, VideoApiClient videoApiClient, TokenEncoder tokenEncoder) {
        this.stsClient = stsClient;
        this.shortLinkClient = shortLinkClient;
        this.authorizationClient = authorizationClient;
        this.videoApiClient = videoApiClient;
        this.tokenEncoder = tokenEncoder;
    }

    @Override
    public HealthcheckResult checkHealth() {
        return checkHealth(true);
    }

    @Override
    public HealthcheckResult checkHealthWithProvisioning() {
        return checkHealth(false);
    }

    @Override
    public String getProvisionStatus(UUID uuid) {
        var stsToken = stsClient.requestToken();
        var accessToken = getAccessToken(stsToken);

        var schedulingInfo = callIfOk(accessToken, () -> videoApiClient.readSchedulingInfo(accessToken.result().getAccessToken().toString(), uuid));

        if(schedulingInfo.ok()) {
            if(schedulingInfo.result().provisionStatus().equals("PROVISIONED_OK")) {
                videoApiClient.closeMeeting(accessToken.result().getAccessToken().toString(), uuid);
            }

            return schedulingInfo.result().provisionStatus();
        }
        else {
            return "ERROR GETTING STATUS";
        }
    }

    private Result<AccessToken> getAccessToken(Result<SecurityToken> securityToken) {
        var accessToken = callIfOk(securityToken, () -> {
            logger.info("Base64 encode token from STS.");
            var encodedToken = tokenEncoder.encode(securityToken.result());

            // Request access token using received SAML token.
            logger.info("Requesting access token from service..");
            return authorizationClient.authorize(encodedToken);
        });

        return accessToken;
    }

    private HealthcheckResult checkHealth(boolean closeMeeting) {
        logger.info("Executing health check.");

        logger.info("About to call STS to get token.");
        var stsToken = stsClient.requestToken();
        var accessToken = getAccessToken(stsToken);

        UUID meetingUuid = null;
        var createMeetingResponse = callIfOk(accessToken, () -> {
            logger.info("Call VideoAPI using access token.");
            var createMeeting = new CreateMeeting();
            createMeeting.setSubject("Healthcheck Meeting - %s".formatted(LocalDateTime.now()));
            createMeeting.setEndTime(OffsetDateTime.now().plusHours(1));
            createMeeting.setStartTime(OffsetDateTime.now());
            createMeeting.setDescription("This is a meeting created by healthcheck service at %s".formatted(LocalDateTime.now()));
            return videoApiClient.createMeeting(accessToken.result().getAccessToken().toString(), createMeeting);
        });

        if(createMeetingResponse.ok()) {
            meetingUuid = createMeetingResponse.result().getUuid();
        }

        logger.info("Call short link service.");
        var shortLinkResponse = callIfOk(createMeetingResponse, () -> shortLinkClient.getShortLink(createMeetingResponse.result().getShortLink()));

        if(closeMeeting) {
            // Closing meeting again by setting end time to now().
            callIfOk(createMeetingResponse, () -> videoApiClient.closeMeeting(accessToken.result().getAccessToken().toString(), createMeetingResponse.result().getUuid()));
        }

        return new HealthcheckResult(createStatus(stsToken),
                createStatus(createMeetingResponse),
                createStatus(shortLinkResponse),
                null,
                createStatus(accessToken),
                meetingUuid);
    }

    private <T> Status createStatus(Result<T> result) {
        return new Status(result.ok(), result.message(), result.responseTime());
    }

    private <T, Z>  Result<T> callIfOk(Result<Z> previousCallStatus, Supplier<Result<T>> nextCall) {
        if(previousCallStatus == null || !previousCallStatus.ok()) {
            return new Result<>(false, "Not called due to previous error.", 0L, null);
        }

        return nextCall.get();
    }
}
