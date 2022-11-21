package dk.medcom.healthcheck.client.security;

import dk.medcom.healthcheck.client.Result;
import io.micrometer.core.annotation.Timed;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.cxf.ws.security.trust.STSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StsClientImpl implements StsClient {
    private static final Logger logger = LoggerFactory.getLogger(StsClientImpl.class);
    private final STSClient client;

    private final String audience;

    public StsClientImpl(STSClient client, String audience) {
        this.client = client;
        this.audience = audience;
    }

    @Override
    @Timed
    public Result<SecurityToken> requestToken() {
        var start = System.currentTimeMillis();
        try {
            var token = client.requestSecurityToken(audience);

            return new Result<>(System.currentTimeMillis()-start, token);
        } catch (Exception e) {
            logger.error("Error requesting token.", e);

            return new Result<>(false, e.getMessage(), System.currentTimeMillis()-start, null);
        }
    }
}
