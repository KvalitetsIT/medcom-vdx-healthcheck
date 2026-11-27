package dk.medcom.healthcheck.client.keycloak;

import org.htmlunit.WebConnection;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.WebResponseData;
import org.htmlunit.util.NameValuePair;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * HtmlUnit connection that records requests and answers them with queued responses.
 */
class FakeBrowserConnection implements WebConnection {
    final List<WebRequest> requests = new ArrayList<>();
    private final Queue<WebResponseData> responses = new LinkedList<>();

    org.htmlunit.WebClient browser() {
        var browser = new org.htmlunit.WebClient();
        browser.setWebConnection(this);
        return browser;
    }

    void respondHtml(int status, String html) {
        responses.add(new WebResponseData(html.getBytes(StandardCharsets.UTF_8), status, "", List.of(new NameValuePair("Content-Type", "text/html; charset=UTF-8"))));
    }

    void respondRedirect(String location) {
        responses.add(new WebResponseData(new byte[0], 302, "Found", List.of(new NameValuePair("Location", location))));
    }

    @Override
    public WebResponse getResponse(WebRequest request) {
        requests.add(request);
        return new WebResponse(responses.remove(), request, 0);
    }

    @Override
    public void close() {
    }
}
