package dk.medcom.healthcheck.client.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import dk.medcom.healthcheck.client.Result;
import org.htmlunit.Page;
import org.htmlunit.WebClientOptions;
import org.htmlunit.html.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Login to Keycloak as a test user through an identity provider (broker) using the authorization code flow with PKCE.
 * The login is done with HtmlUnit in the same way as in medcom-vdx-borgerrettet.
 */
public class KeycloakUserLoginClient implements KeycloakClient {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserLoginClient.class);

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Supplier<org.htmlunit.WebClient> browserFactory;
    private final WebClient webClient;
    private final Duration requestTimeout;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String authUrl;
    private final String logoutUrl;
    private final String redirectUri;
    private final String brokerIdP;
    private final String username;
    private final String password;

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken) {}

    static class LoginException extends RuntimeException {
        LoginException(String message) {
            super(message);
        }
    }

    public KeycloakUserLoginClient(WebClient.Builder webClientBuilder, String tokenUrl, String clientId, String clientSecret, String redirectUri, String brokerIdP, String username, String password) {
        this(org.htmlunit.WebClient::new, webClientBuilder, REQUEST_TIMEOUT, tokenUrl, clientId, clientSecret, redirectUri, brokerIdP, username, password);
    }

    KeycloakUserLoginClient(Supplier<org.htmlunit.WebClient> browserFactory, WebClient.Builder webClientBuilder, Duration requestTimeout, String tokenUrl, String clientId, String clientSecret, String redirectUri, String brokerIdP, String username, String password) {
        this.browserFactory = browserFactory;
        this.webClient = webClientBuilder.build();
        this.requestTimeout = requestTimeout;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authUrl = siblingEndpoint(tokenUrl, "auth");
        this.logoutUrl = siblingEndpoint(tokenUrl, "logout");
        this.redirectUri = redirectUri;
        this.brokerIdP = brokerIdP;
        this.username = username;
        this.password = password;
    }

    @Override
    public Result<Void> login() {
        var start = System.currentTimeMillis();
        try {
            if(authUrl == null) {
                throw new LoginException("Can not login to Keycloak. Token URL does not end with /token.");
            }

            var codeVerifier = generateCodeVerifier();
            var code = getAuthCode(codeVerifier);

            var token = requestToken(codeForm(code, codeVerifier));
            var responseTime = System.currentTimeMillis() - start;

            if(token == null || !isSet(token.accessToken())) {
                throw new LoginException("No access token in response from Keycloak.");
            }

            if(!isSet(token.refreshToken())) {
                throw new LoginException("No refresh token in response from Keycloak. Can not logout.");
            }
            var logoutError = logout(token.refreshToken());
            if(logoutError != null) {
                return new Result<>(false, logoutError, responseTime, null);
            }

            return new Result<>(responseTime, null);
        } catch (LoginException e) {
            logger.error("Keycloak test user login failed: {}", e.getMessage());
            return new Result<>(false, e.getMessage(), System.currentTimeMillis() - start, null);
        } catch (WebClientResponseException e) {
            logger.error("Wrong HTTP status from Keycloak token endpoint {}: {}", tokenUrl, e.getStatusCode().value());
            return new Result<>(false, "Wrong HTTP status from Keycloak: %s".formatted(e.getStatusCode().value()), System.currentTimeMillis() - start, null);
        } catch (Exception e) {
            logger.error("Error logging in to Keycloak {}.", authUrl, e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis() - start, null);
        }
    }

    private String getAuthCode(String codeVerifier) {
        try (org.htmlunit.WebClient wc = browserFactory.get()) {
            WebClientOptions options = wc.getOptions();
            options.setJavaScriptEnabled(false);
            options.setCssEnabled(false);
            options.setTimeout((int) requestTimeout.toMillis());

            // Go to Keycloak login form and choose IdP
            var codeChallenge = generateCodeChallenge(codeVerifier);
            HtmlPage page = wc.getPage(authUrl + "?client_id=" + clientId + "&redirect_uri=" + redirectUri + "&response_type=code&scope=openid" + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256");

            HtmlAnchor idpButton = (HtmlAnchor) page.getElementById("zocial-" + brokerIdP);
            HtmlPage idpLoginPage;
            if (idpButton != null) {
                idpLoginPage = idpButton.click();
            } else {
                logger.warn("Login failed. IdP button not found.");
                throw new LoginException("Login failed. IdP button not found.");
            }

            // Find login form
            HtmlForm form = idpLoginPage.getForms().getFirst();

            HtmlTextInput usernameField = form.getInputByName("username");
            HtmlPasswordInput passwordField = form.getInputByName("password");

            usernameField.setValueAttribute(username);  // Set username
            passwordField.setValueAttribute(password);  // Set password

            // Send form
            options.setRedirectEnabled(false);
            options.setThrowExceptionOnFailingStatusCode(false);
            var resultPage = form.getButtonByName("login").click();

            var location = resultPage.getWebResponse().getResponseHeaderValue("location");
            resultPage = wc.getPage(location);

            location = resultPage.getWebResponse().getResponseHeaderValue("location");

            if (location.contains("first-broker-login")) {
                resultPage = handleFirstBrokerLogin(wc, location);
            }

            location = resultPage.getWebResponse().getResponseHeaderValue("location");

            // Find authorization code in URL
            if (location.contains("?code=")) {
                return location.split("\\?code=")[1].split("&")[0];
            } else if (location.contains("&code=")) {
                return location.split("&code=")[1].split("&")[0];
            } else {
                logger.warn("Login failed. Authorization code not found.");
                throw new LoginException("Login failed. Authorization code not found.");
            }

        } catch (LoginException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Caught exception when retrieving auth code.", e);
            throw new LoginException(e.getMessage());
        }
    }

    private Page handleFirstBrokerLogin(org.htmlunit.WebClient wc, String location) throws IOException {
        var resultPage = wc.getPage(location);
        if (resultPage.isHtmlPage()) {
            HtmlPage res = (HtmlPage) resultPage;

            HtmlElement continueButton = res.getFirstByXPath("//form//button[@type='submit']");

            resultPage = continueButton.click();
        }

        location = resultPage.getWebResponse().getResponseHeaderValue("location");

        if (location.contains("after-first-broker-login")) {
            resultPage = wc.getPage(location);
            if (resultPage.isHtmlPage()) {
                HtmlPage res = (HtmlPage) resultPage;

                HtmlElement continueButton = res.getFirstByXPath("//form//button[@type='submit']");

                resultPage = continueButton.click();
            }
        }

        return resultPage;
    }

    private MultiValueMap<String, String> codeForm(String code, String codeVerifier) {
        var form = clientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return form;
    }

    private String logout(String refreshToken) {
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

    private TokenResponse requestToken(MultiValueMap<String, String> form) {
        return post(tokenUrl, form)
                .bodyToMono(TokenResponse.class)
                .block(requestTimeout);
    }

    private WebClient.ResponseSpec post(String url, MultiValueMap<String, String> form) {
        return webClient.post()
                .uri(URI.create(url))
                .body(BodyInserters.fromFormData(form))
                .retrieve();
    }

    private static String siblingEndpoint(String tokenUrl, String endpoint) {
        var tokenPathSuffix = "/token";
        if(tokenUrl == null || !tokenUrl.endsWith(tokenPathSuffix)) {
            return null;
        }
        return tokenUrl.substring(0, tokenUrl.length() - tokenPathSuffix.length()) + "/" + endpoint;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static String generateCodeVerifier() {
        byte[] code = new byte[32];
        new SecureRandom().nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    static String generateCodeChallenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
