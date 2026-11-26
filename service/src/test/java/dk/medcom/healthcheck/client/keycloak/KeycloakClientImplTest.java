package dk.medcom.healthcheck.client.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakClientImplTest {
    private static final String TOKEN_URL = "http://keycloak/realms/test/protocol/openid-connect/token";
    private static final String LOGOUT_URL = "http://keycloak/realms/test/protocol/openid-connect/logout";

    private final List<ClientRequest> requests = new ArrayList<>();
    private final Queue<Supplier<Mono<ClientResponse>>> responses = new LinkedList<>();

    @Test
    public void testPasswordGrantOk() {
        respond(200, "{\"access_token\":\"abc\",\"token_type\":\"Bearer\"}");
        var client = client(TOKEN_URL, "client", "secret", "user", "p&ss");

        var result = client.login();

        assertTrue(result.ok());
        assertNull(result.message());

        assertEquals(1, requests.size());
        var request = requests.getFirst();
        assertEquals(TOKEN_URL, request.url().toString());
        assertEquals(HttpMethod.POST, request.method());
        assertEquals("client_id=client&client_secret=secret&grant_type=password&username=user&password=p%26ss", body(request));
    }

    @Test
    public void testClientCredentialsGrantWhenNoUsername() {
        respond(200, "{\"access_token\":\"abc\"}");
        var client = client(TOKEN_URL, "client", "secret", "", "");

        assertTrue(client.login().ok());
        assertEquals("client_id=client&client_secret=secret&grant_type=client_credentials", body(requests.getFirst()));
    }

    @Test
    public void testWrongStatus() {
        respond(401, "{\"error\":\"invalid_grant\"}");
        var client = client(TOKEN_URL, "client", null, "user", "wrong");

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("Wrong HTTP status from Keycloak: 401", result.message());
    }

    @Test
    public void testNoAccessToken() {
        respond(200, "{}");
        var client = client(TOKEN_URL, "client", null, "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("No access token in response from Keycloak.", result.message());
    }

    @Test
    public void testIoException() {
        responses.add(() -> Mono.error(new IOException("Connection refused")));
        var client = client(TOKEN_URL, "client", null, "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertTrue(result.message().contains("Connection refused"), result.message());
    }

    @Test
    public void testRequestTimesOut() {
        responses.add(Mono::never);
        var client = new KeycloakClientImpl(webClientBuilder(), Duration.ofMillis(100), TOKEN_URL, "client", "secret", null, null);

        var result = client.login();

        assertFalse(result.ok());
        assertTrue(result.message().contains("Timeout"), result.message());
    }

    @Test
    public void testSessionIsLoggedOutWhenRefreshTokenReturned() {
        respond(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh&1\"}");
        respond(204, null);
        var client = client(TOKEN_URL, "client", "secret", "user", "pass");

        var result = client.login();

        assertTrue(result.ok());
        assertEquals(2, requests.size());
        assertEquals(TOKEN_URL, requests.get(0).url().toString());
        var logout = requests.get(1);
        assertEquals(LOGOUT_URL, logout.url().toString());
        assertEquals(HttpMethod.POST, logout.method());
        assertEquals("client_id=client&client_secret=secret&refresh_token=refresh%261", body(logout));
    }

    @Test
    public void testLogoutFails() {
        respond(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        respond(400, "{\"error\":\"invalid_grant\"}");
        var client = client(TOKEN_URL, "client", "secret", "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("Wrong HTTP status from Keycloak logout: 400", result.message());
    }

    @Test
    public void testLogoutIoException() {
        respond(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        responses.add(() -> Mono.error(new IOException("Connection reset")));
        var client = client(TOKEN_URL, "client", "secret", "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertTrue(result.message().startsWith("Error logging out from Keycloak: "), result.message());
        assertTrue(result.message().contains("Connection reset"), result.message());
    }

    @Test
    public void testLogoutNotPossibleWhenTokenUrlDoesNotEndWithToken() {
        respond(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        var client = client("http://keycloak/some/other/path", "client", "secret", "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("Can not logout from Keycloak. Token URL does not end with /token.", result.message());
        assertEquals(1, requests.size());
    }

    @Test
    public void testNoLogoutWithoutRefreshToken() {
        respond(200, "{\"access_token\":\"abc\"}");
        var client = client(TOKEN_URL, "client", "secret", null, null);

        assertTrue(client.login().ok());
        assertEquals(1, requests.size());
    }

    private KeycloakClientImpl client(String tokenUrl, String clientId, String clientSecret, String username, String password) {
        return new KeycloakClientImpl(webClientBuilder(), tokenUrl, clientId, clientSecret, username, password);
    }

    private WebClient.Builder webClientBuilder() {
        return WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return responses.remove().get();
        });
    }

    private void respond(int status, String json) {
        responses.add(() -> {
            var builder = ClientResponse.create(HttpStatus.valueOf(status))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            if(json != null) {
                builder.body(json);
            }
            return Mono.just(builder.build());
        });
    }

    private String body(ClientRequest request) {
        var httpRequest = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(httpRequest, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return httpRequest.getBodyAsString().block();
    }
}
