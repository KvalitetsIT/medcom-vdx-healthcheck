package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.service.model.HelloServiceOutput;
import dk.medcom.healthcheck.service.model.HelloServiceInput;

public interface HelloService {
    HelloServiceOutput helloServiceBusinessLogic(HelloServiceInput input);
}
