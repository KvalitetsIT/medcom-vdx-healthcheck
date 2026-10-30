package dk.medcom.healthcheck.client.shortlink;

import dk.medcom.healthcheck.client.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ShortLinkClientImpl implements ShortLinkClient {
    private static final Logger logger = LoggerFactory.getLogger(ShortLinkClientImpl.class);
    private final HttpClient httpClient;

    public ShortLinkClientImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Result<Void> getShortLink(String shortLink) {
        var start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(shortLink))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() == 200) {
                return new Result<>(System.currentTimeMillis()-start, null);
            }
            else {
                logger.error("Wrong HTTP status from short link {}: {}", shortLink, response.statusCode());
                return new Result<>(false, "Wrong HTTP status from short link: %s".formatted(response.statusCode()), System.currentTimeMillis()-start, null);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error calling short link {}.", shortLink, e);
            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }
}
