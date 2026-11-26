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

public class KeycloakClientImpl implements KeycloakClient {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakClientImpl.class);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String TOKEN_PATH_SUFFIX = "/token";
    private static final String LOGOUT_PATH_SUFFIX = "/logout";

    private final WebClient webClient;
    private final Duration requestTimeout;
    private final String tokenUrl;
    private final String logoutUrl;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken) {}

    public KeycloakClientImpl(WebClient.Builder webClientBuilder, String tokenUrl, String clientId, String clientSecret, String username, String password) {
        this(webClientBuilder, REQUEST_TIMEOUT, tokenUrl, clientId, clientSecret, username, password);
    }

    KeycloakClientImpl(WebClient.Builder webClientBuilder, Duration requestTimeout, String tokenUrl, String clientId, String clientSecret, String username, String password) {
        this.webClient = webClientBuilder.build();
        this.requestTimeout = requestTimeout;
        this.tokenUrl = tokenUrl;
        this.logoutUrl = toLogoutUrl(tokenUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
    }

    @Override
    public Result<Void> login() {
        var start = System.currentTimeMillis();
        try {
            var form = clientForm();
            if(isSet(username)) {
                form.add("grant_type", "password");
                form.add("username", username);
                form.add("password", password == null ? "" : password);
            }
            else {
                form.add("grant_type", "client_credentials");
            }

            var token = post(tokenUrl, form)
                    .bodyToMono(TokenResponse.class)
                    .block(requestTimeout);
            var responseTime = System.currentTimeMillis() - start;

            if(token == null || !isSet(token.accessToken())) {
                logger.error("No access_token in response from Keycloak token endpoint {}.", tokenUrl);
                return new Result<>(false, "No access token in response from Keycloak.", responseTime, null);
            }

            // A refresh token means Keycloak created a session (password grant). End it again so sessions do not pile up.
            if(isSet(token.refreshToken())) {
                var logoutError = logout(token.refreshToken());
                if(logoutError != null) {
                    return new Result<>(false, logoutError, responseTime, null);
                }
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

    /**
     * @return null if logout succeeded, otherwise an error message.
     */
    private String logout(String refreshToken) {
        if(logoutUrl == null) {
            logger.error("Can not derive Keycloak logout endpoint from token URL {}. Session is not ended.", tokenUrl);
            return "Can not logout from Keycloak. Token URL does not end with %s.".formatted(TOKEN_PATH_SUFFIX);
        }

        try {
            var form = clientForm();
            form.add("refresh_token", refreshToken);

            post(logoutUrl, form)
                    .toBodilessEntity()
                    .block(requestTimeout);

            return null;
        } catch (WebClientResponseException e) {
            logger.error("Wrong HTTP status from Keycloak logout endpoint {}: {}", logoutUrl, e.getStatusCode().value());
            return "Wrong HTTP status from Keycloak logout: %s".formatted(e.getStatusCode().value());
        } catch (Exception e) {
            logger.error("Error logging out from Keycloak {}.", logoutUrl, e);
            return "Error logging out from Keycloak: %s".formatted(e.getMessage());
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

    private WebClient.ResponseSpec post(String url, MultiValueMap<String, String> form) {
        return webClient.post()
                .uri(URI.create(url))
                .body(BodyInserters.fromFormData(form))
                .retrieve();
    }

    private static String toLogoutUrl(String tokenUrl) {
        if(tokenUrl == null || !tokenUrl.endsWith(TOKEN_PATH_SUFFIX)) {
            return null;
        }
        return tokenUrl.substring(0, tokenUrl.length() - TOKEN_PATH_SUFFIX.length()) + LOGOUT_PATH_SUFFIX;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
