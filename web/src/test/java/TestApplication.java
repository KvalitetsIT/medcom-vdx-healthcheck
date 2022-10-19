import dk.medcom.healthcheck.VideoLinkHandlerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class TestApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        System.setProperty("STS_URL_WSDL", "https://sts.vconf-stage.dk/sts/sts.wsdl");
        System.setProperty("service.url", "https://videoapi.vconf-stage.dk/videoapi");
        System.setProperty("STS_PROPERTIES", "sts.properties");
        System.setProperty("keystore.password", "Test1234");
        System.setProperty("keystore.path", "client.jks");

        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current absolute path is: " + s);

        SpringApplication.run((VideoLinkHandlerApplication.class));
    }
}