//package dk.medcom.healthcheck.integrationtest;
//
//import org.junit.Test;
//import org.openapitools.client.ApiClient;
//import org.openapitools.client.ApiException;
//import org.openapitools.client.api.HealthcheckApi;
//
//import static org.junit.Assert.assertNotNull;
//
//public class HealthcheckIT extends AbstractIntegrationTest {
//
//    private final HealthcheckApi helloApi;
//
//    public HealthcheckIT() {
//        var apiClient = new ApiClient();
//        apiClient.setBasePath(getApiBasePath());
//
//        helloApi = new HealthcheckApi(apiClient);
//    }
//
//    @Test
//    public void testCallService() throws ApiException {
//
//        var result = helloApi.v1HealthcheckGet();
//
//        assertNotNull(result);
//    }
//}
