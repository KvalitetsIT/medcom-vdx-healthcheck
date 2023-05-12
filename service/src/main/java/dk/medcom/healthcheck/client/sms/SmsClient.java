package dk.medcom.healthcheck.client.sms;

import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.sms.model.SmsRequest;
import dk.medcom.healthcheck.client.sms.model.SmsResponse;
import dk.medcom.healthcheck.client.sms.model.SmsStatus;

import java.util.List;
import java.util.UUID;

public interface SmsClient {
    Result<SmsResponse> sendSms(String accessToken, UUID meeting, SmsRequest request);

    Result<List<SmsStatus>> getStatus(String accessToken, UUID meeting);
}
