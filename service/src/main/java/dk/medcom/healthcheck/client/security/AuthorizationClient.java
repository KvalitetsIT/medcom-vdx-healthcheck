package dk.medcom.healthcheck.client.security;

import dk.medcom.healthcheck.client.security.model.AccessToken;
import dk.medcom.healthcheck.client.Result;

public interface AuthorizationClient {
    /**
     * Exhange base64 encoded token with session id.
     * @param token Base64 encoded token from STS.
     * @return Session id.
     */
    Result<AccessToken> authorize(String token);
}
