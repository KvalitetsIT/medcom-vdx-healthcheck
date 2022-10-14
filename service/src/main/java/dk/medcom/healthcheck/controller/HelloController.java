package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.service.HelloService;
import dk.medcom.healthcheck.service.model.HelloServiceInput;
import org.openapitools.api.HealthcheckApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
// CORS - Consider if this is needed in your application. Only here to make Swagger UI work.
@CrossOrigin(origins = "http://localhost")
public class HelloController implements HealthcheckApi {
    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);
    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @Override
    public ResponseEntity<org.openapitools.model.HelloResponse> v1HelloPost(org.openapitools.model.HelloRequest helloRequest) {
        logger.debug("Enter POST hello.");

        var serviceInput = new HelloServiceInput(helloRequest.getName());

        var serviceResponse = helloService.helloServiceBusinessLogic(serviceInput);

        var helloResponse = new org.openapitools.model.HelloResponse();
        helloResponse.setName(serviceResponse.name());
        helloResponse.setNow(serviceResponse.now().toOffsetDateTime());

        return ResponseEntity.ok(helloResponse);
    }
}
