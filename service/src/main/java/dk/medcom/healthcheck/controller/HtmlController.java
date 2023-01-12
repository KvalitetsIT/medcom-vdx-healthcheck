package dk.medcom.healthcheck.controller;

import dk.medcom.healthcheck.controller.model.Status;
import dk.medcom.healthcheck.service.HealthcheckService;
import dk.medcom.healthcheck.service.model.HealthcheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class HtmlController {
    private static final Logger logger = LoggerFactory.getLogger(HtmlController.class);
    private final HealthcheckService healthcheckService;

    public HtmlController(HealthcheckService healthcheckService) {
        this.healthcheckService = healthcheckService;
    }

    @RequestMapping(path = "/")
    public ModelAndView get() {
        logger.info("Showing main page.");

        var response = new ModelAndView();
        response.setViewName("index");

        return response;
    }

    @RequestMapping(path = "/execute")
    public ModelAndView execute() {
        logger.info("Executing health check test.");

        var result = healthcheckService.checkHealthWithProvisioning();

        var response = new ModelAndView();
        response.setViewName("result");
        response.addObject("allOk", allOk(result));
        response.addObject("status", createStatus(result));
        response.addObject("uuid", result.meetingUuid());

        return response;
    }

    private List<Status> createStatus(HealthcheckResult result) {
        var l = new ArrayList<>(Arrays.asList(
                createStatus("Get token from STS", result.sts()),
                createStatus("Create access token for VideoAPI", result.accessTokenForVideoApi()),
                createStatus("Create meeting in VideoAPI", result.videoAPi()),
                createStatus("Access shortlink page", result.shortLink())));

        l.add(new Status("Total",
                l.stream().allMatch(Status::ok),
                l.stream().mapToLong(Status::responseTime).sum(),
                l.stream().map(Status::message).filter(Objects::nonNull).collect(Collectors.collectingAndThen(Collectors.joining(","), x -> {
                    if(x.isEmpty()) {
                        return null;
                    }
                    return x;
                }))));

        return l;
    }

    private Status createStatus(String name, dk.medcom.healthcheck.service.model.Status status) {
        return new Status(name, status.ok(), status.responseTime(), status.message());
    }

    private boolean allOk(HealthcheckResult result) {
        return result.sts().ok() &&
                result.videoAPi().ok() &&
                result.shortLink().ok() &&
                result.accessTokenForVideoApi().ok();
    }
}
