package dk.medcom.healthcheck.integrationtest;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.io.FileNotFoundException;

@SpringBootApplication
public class TestApplication extends SpringBootServletInitializer {
    public static void main(String[] args) throws FileNotFoundException {
        new ServiceStarter().startServices();
    }
}
