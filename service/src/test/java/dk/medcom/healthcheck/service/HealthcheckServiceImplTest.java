package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.security.AuthorizationClient;
import dk.medcom.healthcheck.client.security.StsClient;
import dk.medcom.healthcheck.client.security.TokenEncoder;
import dk.medcom.healthcheck.client.security.model.AccessToken;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClient;
import dk.medcom.healthcheck.client.videoapi.VideoApiClient;
import dk.medcom.healthcheck.client.videoapi.model.Meeting;
import dk.medcom.healthcheck.client.videoapi.model.SchedulingInfo;
import dk.medcom.healthcheck.service.model.Status;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;

public class HealthcheckServiceImplTest {
    private HealthcheckService healthcheckService;
    private ShortLinkClient shortLinkClient;
    private StsClient stsClient;
    private AuthorizationClient authorizationClient;
    private VideoApiClient videoApiClient;
    private TokenEncoder tokenEncoder;

    @Before
    public void setup() {
        stsClient = Mockito.mock(StsClient.class);
        shortLinkClient = Mockito.mock(ShortLinkClient.class);
        authorizationClient = Mockito.mock(AuthorizationClient.class);
        videoApiClient = Mockito.mock(VideoApiClient.class);
        tokenEncoder = Mockito.mock(TokenEncoder.class);

        healthcheckService = new HealthcheckServiceImpl(stsClient, shortLinkClient, authorizationClient, videoApiClient, tokenEncoder);
    }

    @Test
    public void testAllOk() {
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var meeting = new Meeting();
        meeting.setShortLink(UUID.randomUUID().toString());
        var videoApiResponse = new Result<>(30L, meeting);
        Mockito.when(videoApiClient.createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(videoApiResponse);

        var shortLinkResponse = new Result<Void>(40L, null);
        Mockito.when(shortLinkClient.getShortLink(meeting.getShortLink())).thenReturn(shortLinkResponse);

        var result = healthcheckService.checkHealth();
        assertNotNull(result);
        assertStatusOk(result.sts(), stsResponse.responseTime());
        assertStatusOk(result.accessTokenForVideoApi(), accessTokenResponse.responseTime());
        assertStatusOk(result.videoAPi(), videoApiResponse.responseTime());
        assertStatusOk(result.shortLink(), shortLinkResponse.responseTime());

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any());
        Mockito.verify(shortLinkClient, times(1)).getShortLink(meeting.getShortLink());
        Mockito.verify(videoApiClient, times(1)).closeMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any());

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }

    @Test
    public void testAllStsNotOk() {
        var stsResponse = new Result<SecurityToken>(false, "STS ERROR", 10L, null);
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var result = healthcheckService.checkHealth();
        assertNotNull(result);
        assertStatusNotOk(result.sts(), stsResponse.responseTime());
        assertStatusNotOk(result.accessTokenForVideoApi(), 0L);
        assertStatusNotOk(result.videoAPi(), 0L);
        assertStatusNotOk(result.shortLink(), 0L);

        Mockito.verify(stsClient, times(1)).requestToken();

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }

    @Test
    public void testGetProvisionStatus() {
        var input = UUID.randomUUID();
        var expectedStatus = "SOME_STATUS";
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var now = OffsetDateTime.now();
        var schedulingInfo = new SchedulingInfo(expectedStatus, now.minusMinutes(10), null);
        var videoApiResponse = new Result<>(30L, schedulingInfo);
        Mockito.when(videoApiClient.readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(videoApiResponse);

        var result = healthcheckService.getProvisionStatus(input);
        assertNotNull(result);
        assertEquals(expectedStatus, result.status());
        assertEquals(0, result.timeToProvision());

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.eq(input));

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }

    @Test
    public void testGetProvisionStatusCloseMeetingOnProvisionedOk() {
        var input = UUID.randomUUID();
        var expectedStatus = "PROVISIONED_OK";
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var now = OffsetDateTime.now();
        var schedulingInfo = new SchedulingInfo(expectedStatus, now.minusMinutes(10), now);
        var schedulingInfoResponse = new Result<>(30L, schedulingInfo);
        Mockito.when(videoApiClient.readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(schedulingInfoResponse);

        var result = healthcheckService.getProvisionStatus(input);
        assertNotNull(result);
        assertEquals(expectedStatus, result.status());
        assertEquals(600000, result.timeToProvision());

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.eq(input));
        Mockito.verify(videoApiClient, times(1)).closeMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.eq(input));

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }

    @Test
    public void testGetProvisionStatusSomeError() {
        var input = UUID.randomUUID();
        var apiStatus = "SOME_STATUS";
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var now = OffsetDateTime.now();
        var schedulingInfo = new SchedulingInfo(apiStatus, now.minusMinutes(10), now);
        var videoApiResponse = new Result<>(false, "some_message", 30L, schedulingInfo);
        Mockito.when(videoApiClient.readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(videoApiResponse);

        var result = healthcheckService.getProvisionStatus(input);
        assertNotNull(result);
        assertEquals("ERROR GETTING STATUS", result.status());
        assertEquals(0, result.timeToProvision());

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).readSchedulingInfo(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.eq(input));

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }

    @Test
    public void testAllVideoApiNotOk() {
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var videoApiResponse = new Result<Meeting>(false, "VIDEO API ERROR", 30L, null);
        Mockito.when(videoApiClient.createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(videoApiResponse);

        var result = healthcheckService.checkHealth();
        assertNotNull(result);
        assertStatusOk(result.sts(), stsResponse.responseTime());
        assertStatusOk(result.accessTokenForVideoApi(), accessTokenResponse.responseTime());
        assertStatusNotOk(result.videoAPi(), videoApiResponse.responseTime());
        assertStatusNotOk(result.shortLink(), 0L);

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any());

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }


    @Test
    public void testAllOkProvisioning() {
        var stsResponse = new Result<>(10L, new SecurityToken());
        Mockito.when(stsClient.requestToken()).thenReturn(stsResponse);

        var token = "TOKEN";
        Mockito.when(tokenEncoder.encode(stsResponse.result())).thenReturn(token);

        var accessToken = new AccessToken();
        accessToken.setAccessToken(UUID.randomUUID());
        var accessTokenResponse = new Result<>(20L, accessToken);
        Mockito.when(authorizationClient.authorize(token)).thenReturn(accessTokenResponse);

        var meeting = new Meeting();
        meeting.setShortLink(UUID.randomUUID().toString());
        meeting.setUuid(UUID.randomUUID());
        var videoApiResponse = new Result<>(30L, meeting);
        Mockito.when(videoApiClient.createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any())).thenReturn(videoApiResponse);

        var shortLinkResponse = new Result<Void>(40L, null);
        Mockito.when(shortLinkClient.getShortLink(meeting.getShortLink())).thenReturn(shortLinkResponse);

        var result = healthcheckService.checkHealthWithProvisioning();
        assertNotNull(result);
        assertStatusOk(result.sts(), stsResponse.responseTime());
        assertStatusOk(result.accessTokenForVideoApi(), accessTokenResponse.responseTime());
        assertStatusOk(result.videoAPi(), videoApiResponse.responseTime());
        assertStatusOk(result.shortLink(), shortLinkResponse.responseTime());
        assertEquals(meeting.getUuid(), result.meetingUuid());

        Mockito.verify(stsClient, times(1)).requestToken();
        Mockito.verify(tokenEncoder, times(1)).encode(stsResponse.result());
        Mockito.verify(authorizationClient, times(1)).authorize(token);
        Mockito.verify(videoApiClient, times(1)).createMeeting(Mockito.eq(accessToken.getAccessToken().toString()), Mockito.any());
        Mockito.verify(shortLinkClient, times(1)).getShortLink(meeting.getShortLink());
        Mockito.verify(videoApiClient, times(0)).closeMeeting(Mockito.any(), Mockito.any());

        Mockito.verifyNoMoreInteractions(stsClient, tokenEncoder, authorizationClient, videoApiClient, shortLinkClient);
    }


    private void assertStatusNotOk(Status status, long expectedResponseTime) {
        assertNotNull(status);
        assertEquals(expectedResponseTime, status.responseTime());
        assertFalse(status.ok());
        assertNotNull(status.message());
    }

    private void assertStatusOk(Status status, long expectedResponseTime) {
        assertNotNull(status);
        assertEquals(expectedResponseTime, status.responseTime());
        assertTrue(status.ok());
        assertNull(status.message());
    }
}