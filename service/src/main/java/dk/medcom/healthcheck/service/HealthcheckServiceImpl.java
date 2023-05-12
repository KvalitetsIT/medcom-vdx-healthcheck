package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.security.AuthorizationClient;
import dk.medcom.healthcheck.client.security.StsClient;
import dk.medcom.healthcheck.client.security.TokenEncoder;
import dk.medcom.healthcheck.client.security.model.AccessToken;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClient;
import dk.medcom.healthcheck.client.sms.SmsClient;
import dk.medcom.healthcheck.client.sms.model.SmsRequest;
import dk.medcom.healthcheck.client.sms.model.SmsResponse;
import dk.medcom.healthcheck.client.videoapi.VideoApiClient;
import dk.medcom.healthcheck.client.videoapi.model.CreateMeeting;
import dk.medcom.healthcheck.service.model.*;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class HealthcheckServiceImpl implements HealthcheckService {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckServiceImpl.class);
    private final StsClient stsClient;
    private final ShortLinkClient shortLinkClient;
    private final AuthorizationClient authorizationClient;
    private final VideoApiClient videoApiClient;
    private final TokenEncoder tokenEncoder;
    private final AuthorizationClient smsAuthorizationClient;
    private final SmsClient smsClient;

    public HealthcheckServiceImpl(StsClient stsClient,
                                  ShortLinkClient shortLinkClient,
                                  AuthorizationClient videoApiAuthorizationClient,
                                  VideoApiClient videoApiClient,
                                  TokenEncoder tokenEncoder,
                                  AuthorizationClient smsAuthorizationClient,
                                  SmsClient smsClient) {
        this.stsClient = stsClient;
        this.shortLinkClient = shortLinkClient;
        this.authorizationClient = videoApiAuthorizationClient;
        this.videoApiClient = videoApiClient;
        this.tokenEncoder = tokenEncoder;
        this.smsAuthorizationClient = smsAuthorizationClient;
        this.smsClient = smsClient;
    }

    @Override
    public HealthcheckResult checkHealth() {
        return checkHealth(true, null);
    }

    @Override
    public HealthcheckResult checkHealthWithProvisioning() {
        return checkHealth(false, null);
    }

    @Override
    public HealthcheckResult checkHealthWithProvisioningAndSms(String phone) {
        return checkHealth(false, phone);
    }

    @Override
    public MeetingStatus getStatus(UUID uuid) {
        var stsToken = stsClient.requestToken();

        var provisionStatus = provisionStatus(stsToken, uuid);
        var smsStatus = smsStatus(stsToken, uuid);

        return new MeetingStatus(provisionStatus, smsStatus);
    }

    @Override
    public ProvisionStatus getProvisionStatus(UUID uuid) {
        var stsToken = stsClient.requestToken();

        return provisionStatus(stsToken, uuid);
    }

    private ProvisionStatus provisionStatus(Result<SecurityToken> stsToken, UUID uuid) {
        Result<AccessToken> accessToken = getVideoApiAccessToken(stsToken);
        var schedulingInfo = callIfOk(accessToken, () -> videoApiClient.readSchedulingInfo(accessToken.result().getAccessToken().toString(), uuid));

        if(schedulingInfo.ok()) {
            if(schedulingInfo.result().provisionStatus().equals("PROVISIONED_OK")) {
                videoApiClient.closeMeeting(accessToken.result().getAccessToken().toString(), uuid);
            }

            var timeToProvision = 0L;
            if(schedulingInfo.result().provisionTimestamp() != null) {
                timeToProvision = (schedulingInfo.result().provisionTimestamp().toEpochSecond()-schedulingInfo.result().createdTime().toEpochSecond())*1000;
            }
            return new ProvisionStatus(schedulingInfo.result().provisionStatus(), timeToProvision);
        }
        else {
            return new ProvisionStatus("ERROR GETTING STATUS", 0);
        }
    }

    private SmsStatus smsStatus(Result<SecurityToken> stsToken, UUID uuid) {
        try {
            Result<AccessToken> accessToken = getSmsApiAccessToken(stsToken);
            var smsStatus = callIfOk(accessToken, () -> smsClient.getStatus(accessToken.result().getAccessToken().toString(), uuid));

            if(smsStatus.ok()) {
                if(smsStatus.result().size() != 1) {
                    return new SmsStatus("UNABLE TO READ SMS STATUS.");
                }

                return new SmsStatus(smsStatus.result().get(0).getStatus());
            }
            else {
                return new SmsStatus("ERROR GETTING STATUS");
            }
        }
        catch(WebClientResponseException.NotFound e) {
            return new SmsStatus("SMS NOT FOUND OR NOT SEND");
        }
    }


    private Result<AccessToken> getVideoApiAccessToken(Result<SecurityToken> securityToken) {
        return callIfOk(securityToken, () -> {
            logger.info("Base64 encode token from STS.");
            var encodedToken = tokenEncoder.encode(securityToken.result());

            // Request access token using received SAML token.
            logger.info("Requesting access token from service..");
            return authorizationClient.authorize(encodedToken);
        });
    }

    private Result<AccessToken> getSmsApiAccessToken(Result<SecurityToken> securityToken) {
        return callIfOk(securityToken, () -> {
            logger.info("Base64 encode token from STS.");
            var encodedToken = tokenEncoder.encode(securityToken.result());

            // Request access token using received SAML token.
            logger.info("Requesting access token from service..");
            return smsAuthorizationClient.authorize(encodedToken);
        });
    }

    private HealthcheckResult checkHealth(boolean closeMeeting, String phone) {
        logger.info("Executing health check.");

        logger.info("About to call STS to get token.");
        var stsToken = stsClient.requestToken();
        var accessToken = getVideoApiAccessToken(stsToken);

        UUID meetingUuid = null;
        var createMeetingResponse = callIfOk(accessToken, () -> {
            logger.info("Call VideoAPI using access token.");
            var createMeeting = new CreateMeeting();
            createMeeting.setSubject("Healthcheck Meeting - %s".formatted(LocalDateTime.now()));
            createMeeting.setEndTime(OffsetDateTime.now().plusHours(1));
            createMeeting.setStartTime(OffsetDateTime.now());
            createMeeting.setDescription("This is a meeting created by healthcheck service at %s".formatted(LocalDateTime.now()));
            createMeeting.setProjectCode("healthcheck");
            return videoApiClient.createMeeting(accessToken.result().getAccessToken().toString(), createMeeting);
        });

        if(createMeetingResponse.ok()) {
            meetingUuid = createMeetingResponse.result().getUuid();
        }

        logger.info("Call short link service.");
        var shortLinkResponse = callIfOk(createMeetingResponse, () -> shortLinkClient.getShortLink(createMeetingResponse.result().getShortLink()));

        Result<SmsResponse> smsResponse = null;
        if(phone != null) {
            smsResponse = callIfOk(createMeetingResponse, () -> {
                var smsRequest = new SmsRequest();
                smsRequest.setMessage("Test message from health check service");
                smsRequest.setTo(phone);

                return smsClient.sendSms(accessToken.result().getAccessToken().toString(), createMeetingResponse.result().getUuid(), smsRequest);
            });
        }

        if(closeMeeting) {
            // Closing meeting again by setting end time to now().
            callIfOk(createMeetingResponse, () -> videoApiClient.closeMeeting(accessToken.result().getAccessToken().toString(), createMeetingResponse.result().getUuid()));
        }

        return new HealthcheckResult(createStatus(stsToken),
                createStatus(createMeetingResponse),
                createStatus(shortLinkResponse),
                smsResponse == null ? Optional.empty() : Optional.of(createStatus(smsResponse)),
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
