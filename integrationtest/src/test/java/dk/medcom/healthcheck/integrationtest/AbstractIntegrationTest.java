package dk.medcom.healthcheck.integrationtest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public abstract class AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    static final String KEYCLOAK_TOKEN_PATH = "/realms/test/protocol/openid-connect/token";
    static final String KEYCLOAK_CLIENT_ID = "healthcheck";
    static final String KEYCLOAK_CLIENT_SECRET = "healthcheck-secret";
    static final String KEYCLOAK_USERNAME = "testuser";
    static final String KEYCLOAK_PASSWORD = "testpassword";
    static final String KEYCLOAK_LOGOUT_PATH = "/realms/test/protocol/openid-connect/logout";
    private static final String KEYCLOAK_REFRESH_TOKEN = "user-refresh-token";
    private static final String KEYCLOAK_NETWORK_ALIAS = "keycloak";

    public static GenericContainer<?> healthcheckService;
    private static MockServerContainer keycloakMock;
    private static String apiBasePath;

    @AfterAll
    public static void afterAll() {
        if(healthcheckService != null) {
            healthcheckService.getDockerClient().stopContainerCmd(healthcheckService.getContainerId()).exec();
        }
    }

    @BeforeAll
    public static void beforeAll() throws IOException, InterruptedException {
        setup();
    }

    private static void setup() throws IOException, InterruptedException {
        boolean runInDocker = Boolean.getBoolean("runInDocker");
        logger.info("Running integration test in docker container: {}", runInDocker);

        var dockerNetwork = Network.newNetwork();
        startKeycloakMock(dockerNetwork);

        var serviceStarter = new ServiceStarter();

        if(runInDocker) {
            var keycloakTokenUrl = "http://" + KEYCLOAK_NETWORK_ALIAS + ":" + MockServerContainer.PORT + KEYCLOAK_TOKEN_PATH;
            healthcheckService = serviceStarter.startServicesInDocker(dockerNetwork, keycloakTokenUrl);
            apiBasePath = "http://" + healthcheckService.getHost() + ":" + healthcheckService.getMappedPort(8080);
        } else {
            serviceStarter.startServices(keycloakMock.getEndpoint() + KEYCLOAK_TOKEN_PATH);
            apiBasePath = "http://localhost:8080";
        }
    }

    private static void startKeycloakMock(Network dockerNetwork) throws IOException, InterruptedException {
        keycloakMock = new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                .withNetwork(dockerNetwork)
                .withNetworkAliases(KEYCLOAK_NETWORK_ALIAS);
        keycloakMock.start();

        // Token endpoint accepts client credentials grant and password grant for the test user. Everything else gives 401.
        // Logout endpoint accepts the refresh token issued to the test user.
        var expectations = """
                [
                  {
                    "priority": 10,
                    "httpRequest": {
                      "method": "POST",
                      "path": "%1$s",
                      "body": { "type": "PARAMETERS", "parameters": { "grant_type": ["client_credentials"], "client_id": ["%2$s"], "client_secret": ["%3$s"] } }
                    },
                    "httpResponse": { "statusCode": 200, "headers": { "Content-Type": ["application/json"] }, "body": "{\\"access_token\\":\\"client-token\\",\\"token_type\\":\\"Bearer\\"}" }
                  },
                  {
                    "priority": 10,
                    "httpRequest": {
                      "method": "POST",
                      "path": "%1$s",
                      "body": { "type": "PARAMETERS", "parameters": { "grant_type": ["password"], "client_id": ["%2$s"], "username": ["%4$s"], "password": ["%5$s"] } }
                    },
                    "httpResponse": { "statusCode": 200, "headers": { "Content-Type": ["application/json"] }, "body": "{\\"access_token\\":\\"user-token\\",\\"refresh_token\\":\\"%6$s\\",\\"token_type\\":\\"Bearer\\"}" }
                  },
                  {
                    "priority": 10,
                    "httpRequest": {
                      "method": "POST",
                      "path": "%7$s",
                      "body": { "type": "PARAMETERS", "parameters": { "client_id": ["%2$s"], "refresh_token": ["%6$s"] } }
                    },
                    "httpResponse": { "statusCode": 204 }
                  },
                  {
                    "priority": 0,
                    "httpRequest": { "method": "POST", "path": "%1$s" },
                    "httpResponse": { "statusCode": 401, "headers": { "Content-Type": ["application/json"] }, "body": "{\\"error\\":\\"invalid_grant\\"}" }
                  }
                ]
                """.formatted(KEYCLOAK_TOKEN_PATH, KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET, KEYCLOAK_USERNAME, KEYCLOAK_PASSWORD, KEYCLOAK_REFRESH_TOKEN, KEYCLOAK_LOGOUT_PATH);

        try(var httpClient = HttpClient.newHttpClient()) {
            var response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(keycloakMock.getEndpoint() + "/mockserver/expectation"))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(expectations))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() != 201) {
                throw new IllegalStateException("Could not create Keycloak mock expectations. Status: %s, body: %s".formatted(response.statusCode(), response.body()));
            }
        }
    }

    /**
     * @return number of requests the Keycloak mock has received on the given path.
     */
    static int keycloakRequestCount(String path) throws IOException, InterruptedException {
        try(var httpClient = HttpClient.newHttpClient()) {
            var response = httpClient.send(HttpRequest.newBuilder()
                            .uri(URI.create(keycloakMock.getEndpoint() + "/mockserver/retrieve?type=REQUESTS&format=JSON"))
                            .PUT(HttpRequest.BodyPublishers.ofString("{\"path\": \"%s\"}".formatted(path)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return com.google.gson.JsonParser.parseString(response.body()).getAsJsonArray().size();
        }
    }

    String getApiBasePath() {
        return apiBasePath;
    }
}
