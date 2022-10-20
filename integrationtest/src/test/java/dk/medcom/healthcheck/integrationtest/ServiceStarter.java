package dk.medcom.healthcheck.integrationtest;

import dk.medcom.healthcheck.VideoLinkHandlerApplication;
import org.springframework.boot.SpringApplication;


public class ServiceStarter {
    private String jdbcUrl;

    public void startServices() {
        // Development configuration
        System.setProperty("spring.thymeleaf.cache", "false");

        // Application configuration.
        System.setProperty("STS_URL_WSDL", "https://sts.vconf-stage.dk/sts/sts.wsdl");
        System.setProperty("VIDEOAPI_ENDPOINT", "https://videoapi.vconf-stage.dk/videoapi");
        System.setProperty("STS_PROPERTIES", "sts.properties");
        System.setProperty("STS_TRUST_CERT", "sts.crt");
        System.setProperty("STS_STORE_PASSWORD", "Test1234");
        System.setProperty("STS_STORE", "client.jks");

        SpringApplication.run((VideoLinkHandlerApplication.class));
    }
}
