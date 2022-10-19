package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.service.HealthcheckService;
import org.junit.Before;
import org.mockito.Mockito;

public class HealthcheckControllerTest {
    private HealthcheckController healthcheckController;
    private HealthcheckService healthcheckService;

    @Before
    public void setup() {
        healthcheckService = Mockito.mock(HealthcheckService.class);

        healthcheckController = new HealthcheckController(healthcheckService);
    }
}
