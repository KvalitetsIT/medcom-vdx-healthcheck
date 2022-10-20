package dk.medcom.healthcheck.client.shortlink;

import dk.medcom.healthcheck.client.Result;

public interface ShortLinkClient {
    Result<Void> getShortLink(String shortLink);
}
