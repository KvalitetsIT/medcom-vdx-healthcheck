package dk.medcom.healthcheck.integrationtest;

import org.junit.jupiter.api.Test;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.HealthcheckApi;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
