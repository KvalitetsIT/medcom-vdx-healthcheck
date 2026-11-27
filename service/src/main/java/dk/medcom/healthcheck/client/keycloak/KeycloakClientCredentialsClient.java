package dk.medcom.healthcheck.client.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import dk.medcom.healthcheck.client.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;

/**
 * Login to Keycloak using the client credentials grant.
 */
public class KeycloakClientCredentialsClient implements KeycloakClient {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakClientCredentialsClient.class);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final Duration requestTimeout;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    public KeycloakClientCredentialsClient(WebClient.Builder webClientBuilder, String tokenUrl, String clientId, String clientSecret) {
        this(webClientBuilder, REQUEST_TIMEOUT, tokenUrl, clientId, clientSecret);
    }

    KeycloakClientCredentialsClient(WebClient.Builder webClientBuilder, Duration requestTimeout, String tokenUrl, String clientId, String clientSecret) {
        this.webClient = webClientBuilder.build();
        this.requestTimeout = requestTimeout;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public Result<Void> login() {
        var start = System.currentTimeMillis();
        try {
            var form = clientForm();
            form.add("grant_type", "client_credentials");

            var token = requestToken(form);
            var responseTime = System.currentTimeMillis() - start;

            if(token == null || !isSet(token.accessToken())) {
                logger.error("No access_token in response from Keycloak token endpoint {}.", tokenUrl);
                return new Result<>(false, "No access token in response from Keycloak.", responseTime, null);
            }

            return new Result<>(responseTime, null);
        } catch (WebClientResponseException e) {
            logger.error("Wrong HTTP status from Keycloak token endpoint {}: {}", tokenUrl, e.getStatusCode().value());
            return new Result<>(false, "Wrong HTTP status from Keycloak: %s".formatted(e.getStatusCode().value()), System.currentTimeMillis() - start, null);
        } catch (Exception e) {
            logger.error("Error logging in to Keycloak {}.", tokenUrl, e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis() - start, null);
        }
    }

    private MultiValueMap<String, String> clientForm() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        if(isSet(clientSecret)) {
            form.add("client_secret", clientSecret);
        }
        return form;
    }

    private TokenResponse requestToken(MultiValueMap<String, String> form) {
        return webClient.post()
                .uri(URI.create(tokenUrl))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block(requestTimeout);
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
