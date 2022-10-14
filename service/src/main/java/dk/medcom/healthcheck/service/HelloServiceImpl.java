package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HelloServiceInput;
import dk.medcom.healthcheck.service.model.HelloServiceOutput;

import java.time.ZonedDateTime;

public class HelloServiceImpl implements HelloService {
    @Override
    public HelloServiceOutput helloServiceBusinessLogic(HelloServiceInput input) {
        return new HelloServiceOutput(input.name(), ZonedDateTime.now());
    }
}
