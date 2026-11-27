package dk.medcom.healthcheck.integrationtest;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

public abstract class AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    // The health check logs in to realm "test" (keycloak/test-realm.json) through the identity provider
    // KEYCLOAK_BROKER_IDP, which is realm "idp" (keycloak/idp-realm.json) where the test user is defined.
    static final String KEYCLOAK_REALM = "test";
    static final String KEYCLOAK_IDP_REALM = "idp";
    static final String KEYCLOAK_BROKER_IDP = "test-idp";
    static final String KEYCLOAK_TOKEN_PATH = "/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/token";
    static final String KEYCLOAK_CLIENT_ID = "healthcheck";
    static final String KEYCLOAK_CLIENT_SECRET = "healthcheck-secret";
    static final String KEYCLOAK_REDIRECT_URI = "http://localhost/healthcheck/callback";
    static final String KEYCLOAK_USERNAME = "testuser";
    static final String KEYCLOAK_PASSWORD = "testpassword";
    private static final String KEYCLOAK_ADMIN_USERNAME = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_NETWORK_ALIAS = "keycloak";
    private static final int KEYCLOAK_PORT = 8080;
    private static final String KEYCLOAK_THEME_IMAGE = "kvalitetsit/medcom-vdx-keycloak-theme:1.4.4";

    public static GenericContainer<?> healthcheckService;
    private static GenericContainer<?> keycloak;
    private static String apiBasePath;

    @AfterAll
    public static void afterAll() {
        if(healthcheckService != null) {
            healthcheckService.getDockerClient().stopContainerCmd(healthcheckService.getContainerId()).exec();
        }
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        setup();
    }

    private static void setup() throws IOException {
        boolean runInDocker = Boolean.getBoolean("runInDocker");
        logger.info("Running integration test in docker container: {}", runInDocker);

        var dockerNetwork = Network.newNetwork();
        startKeycloak(dockerNetwork);
        // Keycloak's pages must be reachable from where the health check runs.
        createIdentityProvider(runInDocker ? "http://" + KEYCLOAK_NETWORK_ALIAS + ":" + KEYCLOAK_PORT : keycloakBaseUrl());

        var serviceStarter = new ServiceStarter();

        if(runInDocker) {
            var keycloakTokenUrl = "http://" + KEYCLOAK_NETWORK_ALIAS + ":" + KEYCLOAK_PORT + KEYCLOAK_TOKEN_PATH;
            healthcheckService = serviceStarter.startServicesInDocker(dockerNetwork, keycloakTokenUrl);
            apiBasePath = "http://" + healthcheckService.getHost() + ":" + healthcheckService.getMappedPort(8080);
        } else {
            serviceStarter.startServices(keycloakBaseUrl() + KEYCLOAK_TOKEN_PATH);
            apiBasePath = "http://localhost:8080";
        }
    }

    /**
     * Start a real Keycloak, so the health check is tested against Keycloak's actual login pages.
     */
    private static void startKeycloak(Network dockerNetwork) throws IOException {
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.4.0")
                .withCopyFileToContainer(MountableFile.forHostPath(keycloakThemeJar()), "/opt/keycloak/providers/medcom-vdx-keycloak-theme.jar")
                .withNetwork(dockerNetwork)
                .withNetworkAliases(KEYCLOAK_NETWORK_ALIAS)
                .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/test-realm.json"), "/opt/keycloak/data/import/test-realm.json")
                .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/idp-realm.json"), "/opt/keycloak/data/import/idp-realm.json")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KEYCLOAK_ADMIN_USERNAME)
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KEYCLOAK_ADMIN_PASSWORD)
                .withCommand("start-dev", "--import-realm")
                .withExposedPorts(KEYCLOAK_PORT)
                .waitingFor(Wait.forHttp("/realms/" + KEYCLOAK_REALM).forPort(KEYCLOAK_PORT).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3));
        keycloak.start();
        keycloak.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("keycloak")));
    }

    /**
     * The MedCom login theme used by the broker realm, as in medcom-vdx-borgerrettet. The IdP button is found by its id from this theme.
     */
    private static Path keycloakThemeJar() throws IOException {
        try(var theme = new GenericContainer<>(KEYCLOAK_THEME_IMAGE).withCommand("sleep", "300")) {
            theme.start();
            var themeJar = Files.createTempFile("theme", ".jar");
            theme.copyFileFromContainer("/module/medcom-vdx-keycloak-theme-1.0.0.jar", themeJar.toString());
            Files.setPosixFilePermissions(themeJar, Set.of(PosixFilePermission.OTHERS_READ, PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ));
            return themeJar;
        }
    }

    /**
     * Create the identity provider in the broker realm. It is created here as the authorization URL used by the browser
     * depends on where the health check runs. Back channel calls are made by Keycloak to itself. User info is disabled
     * as the issuer of the token differs from the back channel URL; the claims are taken from the ID token instead.
     */
    private static void createIdentityProvider(String browserBaseUrl) throws IOException {
        var idpUrl = "/realms/" + KEYCLOAK_IDP_REALM + "/protocol/openid-connect";
        var backChannelUrl = "http://localhost:" + KEYCLOAK_PORT + idpUrl;
        var identityProvider = """
                {
                  "alias": "%s",
                  "providerId": "oidc",
                  "enabled": true,
                  "config": {
                    "authorizationUrl": "%s",
                    "tokenUrl": "%s",
                    "disableUserInfo": "true",
                    "logoutUrl": "%s",
                    "backchannelSupported": "true",
                    "clientId": "broker",
                    "clientSecret": "broker-secret",
                    "clientAuthMethod": "client_secret_post",
                    "defaultScope": "openid profile email",
                    "validateSignature": "false",
                    "syncMode": "FORCE"
                  }
                }
                """.formatted(KEYCLOAK_BROKER_IDP, browserBaseUrl + idpUrl + "/auth", backChannelUrl + "/token", backChannelUrl + "/logout");

        try(var httpClient = HttpClient.newHttpClient()) {
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(keycloakBaseUrl() + "/admin/realms/" + KEYCLOAK_REALM + "/identity-provider/instances"))
                            .header("Authorization", "Bearer " + adminToken(httpClient))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(identityProvider))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() != 201) {
                throw new IllegalStateException("Could not create identity provider. Status: %s, body: %s".formatted(response.statusCode(), response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static String keycloakBaseUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(KEYCLOAK_PORT);
    }

    /**
     * @return number of active Keycloak sessions of the test user in the given realm.
     */
    static int keycloakUserSessionCount(String realm) throws IOException, InterruptedException {
        try(var httpClient = HttpClient.newHttpClient()) {
            var adminToken = adminToken(httpClient);
            var userId = keycloakUserId(httpClient, adminToken, realm);
            if(userId == null) {
                return 0;
            }

            var sessions = adminGet(httpClient, adminToken, keycloakBaseUrl() + "/admin/realms/" + realm + "/users/" + userId + "/sessions");
            return JsonParser.parseString(sessions).getAsJsonArray().size();
        }
    }

    /**
     * Wait until the health check run by the scheduler at startup has logged in and out, so it does not interfere with the test.
     * Then delete the test user in the broker realm, so the next login is a first broker login.
     */
    static void awaitScheduledKeycloakLoginAndDeleteBrokerUser() throws IOException, InterruptedException {
        try(var httpClient = HttpClient.newHttpClient()) {
            var deadline = System.currentTimeMillis() + Duration.ofMinutes(1).toMillis();
            String userId;
            while((userId = keycloakUserId(httpClient, adminToken(httpClient), KEYCLOAK_REALM)) == null
                    || keycloakUserSessionCount(KEYCLOAK_REALM) != 0
                    || keycloakUserSessionCount(KEYCLOAK_IDP_REALM) != 0) {
                if(System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("Scheduled health check did not login to Keycloak.");
                }
                Thread.sleep(500);
            }

            var response = httpClient.send(HttpRequest.newBuilder(URI.create(keycloakBaseUrl() + "/admin/realms/" + KEYCLOAK_REALM + "/users/" + userId))
                            .header("Authorization", "Bearer " + adminToken(httpClient))
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() != 204) {
                throw new IllegalStateException("Could not delete user. Status: %s, body: %s".formatted(response.statusCode(), response.body()));
            }
        }
    }

    private static String keycloakUserId(HttpClient httpClient, String adminToken, String realm) throws IOException, InterruptedException {
        var users = adminGet(httpClient, adminToken, keycloakBaseUrl() + "/admin/realms/" + realm + "/users?exact=true&username=" + URLEncoder.encode(KEYCLOAK_USERNAME, StandardCharsets.UTF_8));
        var usersArray = JsonParser.parseString(users).getAsJsonArray();
        return usersArray.isEmpty() ? null : usersArray.get(0).getAsJsonObject().get("id").getAsString();
    }

    /**
     * Admin token for the test only. admin-cli in the master realm only supports the password grant.
     */
    private static String adminToken(HttpClient httpClient) throws IOException, InterruptedException {
        var tokenResponse = httpClient.send(HttpRequest.newBuilder(URI.create(keycloakBaseUrl() + "/realms/master/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=admin-cli&username=%s&password=%s".formatted(KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(tokenResponse.body()).getAsJsonObject().get("access_token").getAsString();
    }

    private static String adminGet(HttpClient httpClient, String adminToken, String url) throws IOException, InterruptedException {
        var response = httpClient.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak admin request %s failed. Status: %s, body: %s".formatted(url, response.statusCode(), response.body()));
        }
        return response.body();
    }

    String getApiBasePath() {
        return apiBasePath;
    }
}
