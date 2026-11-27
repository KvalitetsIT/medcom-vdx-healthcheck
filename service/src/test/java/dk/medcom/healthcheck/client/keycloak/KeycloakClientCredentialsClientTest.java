package dk.medcom.healthcheck.client.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakClientCredentialsClientTest {
    private static final String TOKEN_URL = "http://keycloak/realms/test/protocol/openid-connect/token";

    private final FakeKeycloak keycloak = new FakeKeycloak();

    @Test
    public void testClientCredentialsGrantOk() {
        keycloak.respondJson(200, "{\"access_token\":\"abc\",\"token_type\":\"Bearer\"}");
        var client = client("client", "s&cret");

        var result = client.login();

        assertTrue(result.ok());
        assertNull(result.message());

        assertEquals(1, keycloak.requests.size());
        var request = keycloak.requests.getFirst();
        assertEquals(TOKEN_URL, request.url().toString());
        assertEquals(HttpMethod.POST, request.method());
        assertEquals("client_id=client&client_secret=s%26cret&grant_type=client_credentials", FakeKeycloak.body(request));
    }

    @Test
    public void testPublicClientSendsNoSecret() {
        keycloak.respondJson(200, "{\"access_token\":\"abc\"}");
        var client = client("client", "");

        assertTrue(client.login().ok());
        assertEquals("client_id=client&grant_type=client_credentials", FakeKeycloak.body(keycloak.requests.getFirst()));
    }

    @Test
    public void testWrongStatus() {
        keycloak.respondJson(401, "{\"error\":\"invalid_grant\"}");
        var client = client("client", null);

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("Wrong HTTP status from Keycloak: 401", result.message());
    }

    @Test
    public void testNoAccessToken() {
        keycloak.respondJson(200, "{}");
        var client = client("client", null);

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("No access token in response from Keycloak.", result.message());
    }

    @Test
    public void testIoException() {
        keycloak.respond(request -> Mono.error(new IOException("Connection refused")));
        var client = client("client", null);

        var result = client.login();

        assertFalse(result.ok());
        assertTrue(result.message().contains("Connection refused"), result.message());
    }

    @Test
    public void testRequestTimesOut() {
        keycloak.respond(request -> Mono.never());
        var client = new KeycloakClientCredentialsClient(keycloak.webClientBuilder(), Duration.ofMillis(100), TOKEN_URL, "client", "secret");

        var result = client.login();

        assertFalse(result.ok());
        assertTrue(result.message().contains("Timeout"), result.message());
    }

    private KeycloakClientCredentialsClient client(String clientId, String clientSecret) {
        return new KeycloakClientCredentialsClient(keycloak.webClientBuilder(), TOKEN_URL, clientId, clientSecret);
    }
}
