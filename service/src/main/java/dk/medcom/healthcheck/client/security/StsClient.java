package dk.medcom.healthcheck.client.security;

import dk.medcom.healthcheck.client.Result;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;

public interface StsClient {
    Result<SecurityToken> requestToken();
}
