package dk.medcom.healthcheck.integrationtest;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.HealthcheckApi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HealthcheckIT extends AbstractIntegrationTest {

    private final HealthcheckApi healthcheckApi;

    public HealthcheckIT() {
        var apiClient = new ApiClient();
        apiClient.setBasePath(getApiBasePath());

        healthcheckApi = new HealthcheckApi(apiClient);
    }

    @Test
    public void testCallService() throws ApiException {

        var result = healthcheckApi.v1StatusUuidGet(UUID.randomUUID());

        assertNotNull(result);
    }

    /**
     * The first login creates the user in the broker realm (first broker login). The second login uses the existing user.
     */
    @Test
    public void testKeycloakLogin() throws Exception {
        awaitScheduledKeycloakLoginAndDeleteBrokerUser();

        assertKeycloakLoginOk();
        assertKeycloakLoginOk();
    }

    private void assertKeycloakLoginOk() throws Exception {
        // Called without the generated client as it rejects responses without the required "sms" field.
        try(var httpClient = HttpClient.newHttpClient()) {
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(getApiBasePath() + "/v1/healthcheck")).GET().build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());

            var body = JsonParser.parseString(response.body()).getAsJsonObject();
            var clientCredentials = body.getAsJsonObject("keycloak-client-credentials");
            var user = body.getAsJsonObject("keycloak-user");

            assertNotNull(clientCredentials, response.body());
            assertNotNull(user, response.body());
            assertTrue(clientCredentials.get("status").getAsBoolean(), response.body());
            assertTrue(user.get("status").getAsBoolean(), response.body());
        }
        assertEquals(0, keycloakUserSessionCount(KEYCLOAK_REALM), "Test user session should be logged out.");
        assertEquals(0, keycloakUserSessionCount(KEYCLOAK_IDP_REALM), "Test user session in the identity provider should be logged out.");
    }
}
