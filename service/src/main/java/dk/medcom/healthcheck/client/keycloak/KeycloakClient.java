package dk.medcom.healthcheck.client.keycloak;

import dk.medcom.healthcheck.client.Result;

public interface KeycloakClient {
    /**
     * Perform a login against Keycloak's token endpoint.
     */
    Result<Void> login();
}
