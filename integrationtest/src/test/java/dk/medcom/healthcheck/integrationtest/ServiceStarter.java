package dk.medcom.healthcheck.integrationtest;

import dk.medcom.healthcheck.VideoLinkHandlerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.FileNotFoundException;


public class ServiceStarter {
    private static final Logger logger = LoggerFactory.getLogger(ServiceStarter.class);
    private static final Logger serviceLogger = LoggerFactory.getLogger("medcom-vdx-healthcheck");

    public void startServices() throws FileNotFoundException {
        // Development configuration
        System.setProperty("spring.thymeleaf.cache", "false");

        // Application configuration.
        var clientKeystore = ResourceUtils.getFile("classpath:clientd.jks");

        System.setProperty("STS_URL_WSDL", "http://localhost/sts/sts.wsdl");
        System.setProperty("VIDEOAPI_ENDPOINT", "http://localhost/videoapi");
        System.setProperty("SMS_ENDPOINT", "http://localhost/sms");
        System.setProperty("STS_PROPERTIES", "sts.properties");
        System.setProperty("STS_TRUST_CERT", "sts.crt");
        System.setProperty("STS_STORE_PASSWORD", "Test1234");
        System.setProperty("STS_STORE", clientKeystore.getPath());
        System.setProperty("HEALTHCHECK_RATE", "PT5M");

        System.setProperty("LOG_LEVEL", "DEBUG");

        SpringApplication.run((VideoLinkHandlerApplication.class));
    }

    public GenericContainer<?> startServicesInDocker() {
        Network dockerNetwork = Network.newNetwork();
        GenericContainer<?> service;

        // Start service
        service = new GenericContainer<>("local/medcom-vdx-healthcheck-qa:dev")
                .withClasspathResourceMapping("./client/cert.pem", "/client/cert.pem", BindMode.READ_ONLY)
                .withClasspathResourceMapping("./client/key.pem", "/client/key.pem", BindMode.READ_ONLY)

                .withFileSystemBind("/tmp", "/jacoco-output", BindMode.READ_WRITE)
                .withEnv("JVM_OPTS", "-javaagent:/jacoco/jacocoagent.jar=output=file,destfile=/jacoco-output/jacoco-it.exec,dumponexit=true -cp integrationtest.jar");

        service.withNetwork(dockerNetwork)
                .withNetworkAliases("medcom-vdx-healthcheck")

                .withEnv("LOG_LEVEL", "DEBUG")

                .withEnv("STS_PROPERTIES", "sts.properties")
                .withEnv("STS_URL_WSDL", "http://localhost/sts/sts.wsdl")
                .withEnv("VIDEOAPI_ENDPOINT", "http://localhost/videoapi")
                .withEnv("SMS_ENDPOINT", "http://localhost/sms")
                .withEnv("STS_TRUST_CERT", "/client/cert.pem")
                .withEnv("STS_CLIENT_KEY", "/client/key.pem")
                .withEnv("STS_CLIENT_CERT", "/client/cert.pem")
                .withEnv("HEALTHCHECK_RATE", "PT5M")

                .withExposedPorts(8081,8080)
                .waitingFor(Wait.forHttp("/actuator").forPort(8081).forStatusCode(200));
        service.start();
        attachLogger(serviceLogger, service);

        return service;
    }

    private void attachLogger(Logger logger, GenericContainer<?> container) {
        ServiceStarter.logger.info("Attaching logger to container: {}", container.getContainerInfo().getName());
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
        container.followOutput(logConsumer);
    }
}
