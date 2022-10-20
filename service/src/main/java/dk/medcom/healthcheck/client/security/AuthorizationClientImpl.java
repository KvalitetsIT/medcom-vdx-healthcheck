package dk.medcom.healthcheck.client.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.medcom.healthcheck.client.Result;
import dk.medcom.healthcheck.client.security.model.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

public class AuthorizationClientImpl implements AuthorizationClient {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationClientImpl.class);
    private final String tokenUrl;
    private final WebClient webClient;
    public AuthorizationClientImpl(String tokenUrl, WebClient.Builder webClientBuilder) {
        this.tokenUrl = tokenUrl;
        this.webClient = webClientBuilder
                .baseUrl(tokenUrl)
                .build();
    }

    @Override
    public Result<AccessToken> authorize(String token) {
        var start = System.currentTimeMillis();

        try {
            var request = "saml-token=" + token;
            var response =  webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return new Result<>(System.currentTimeMillis()-start, new ObjectMapper().readValue(response, AccessToken.class));
        } catch (Exception e) {
            logger.error("Error creating access token.", e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }
}
