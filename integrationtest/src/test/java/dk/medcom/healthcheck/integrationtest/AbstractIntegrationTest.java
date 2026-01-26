package dk.medcom.healthcheck.integrationtest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.FileNotFoundException;

public abstract class AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    public static GenericContainer<?> healthcheckService;
    private static String apiBasePath;

    @AfterAll
    public static void afterAll() {
        if(healthcheckService != null) {
            healthcheckService.getDockerClient().stopContainerCmd(healthcheckService.getContainerId()).exec();
        }
    }

    @BeforeAll
    public static void beforeAll() throws FileNotFoundException {
        setup();
    }

    private static void setup() throws FileNotFoundException {
        boolean runInDocker = Boolean.getBoolean("runInDocker");
        logger.info("Running integration test in docker container: {}", runInDocker);

        ServiceStarter serviceStarter;
        serviceStarter = new ServiceStarter();

        if(runInDocker) {
            healthcheckService = serviceStarter.startServicesInDocker();
            apiBasePath = "http://" + healthcheckService.getHost() + ":" + healthcheckService.getMappedPort(8080);
        } else {
            serviceStarter.startServices();
            apiBasePath = "http://localhost:8080";
        }
    }

    String getApiBasePath() {
        return apiBasePath;
    }
}
