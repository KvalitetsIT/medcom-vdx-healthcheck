package dk.medcom.healthcheck.client.sms;

import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.sms.model.SmsRequest;
import dk.medcom.healthcheck.client.sms.model.SmsResponse;
import dk.medcom.healthcheck.client.sms.model.SmsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.UUID;

public class SmsClientImpl implements SmsClient {
    private static final Logger logger = LoggerFactory.getLogger(SmsClientImpl.class);
    private final WebClient webClient;

    public SmsClientImpl(WebClient.Builder webClientBuilder, String endpoint) {
        logger.info("Video API endpoint: {}", endpoint);
        webClient = webClientBuilder
                .baseUrl(endpoint)
                .build();
    }

    @Override
    public Result<SmsResponse> sendSms(String accessToken, UUID meeting, SmsRequest request) {
        long start = System.currentTimeMillis();
        try {
            var smsResponse = webClient.post()
                    .uri("/v1/meeting/{uuid}", meeting.toString())
                    .header("Authorization", "Holder-of-key " + accessToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SmsResponse.class)
                    .block();

            return new Result<>(System.currentTimeMillis()-start, smsResponse);
        }
        catch(Exception e) {
            logger.error("Error calling SMS API.", e);
            if(e instanceof WebClientResponseException we) {
                logger.error(we.getResponseBodyAsString());
            }
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }

    @Override
    public Result<List<SmsStatus>> getStatus(String accessToken, UUID meeting) {
        long start = System.currentTimeMillis();
        try {
            List<SmsStatus> smsStatus = webClient.get()
                    .uri("/v1/meeting/{uuid}", meeting)
                    .header("Authorization", "Holder-of-key " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<SmsStatus>>() {})
                    .block();

            return new Result<>(System.currentTimeMillis()-start, smsStatus);
        }
        catch(WebClientResponseException.NotFound e) {
            logger.info("No sms status found. Maybe it was not send?");
            throw e;
        }
        catch(Exception e) {
            logger.error("Error reading sms status from VideoAPI.", e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }
}
