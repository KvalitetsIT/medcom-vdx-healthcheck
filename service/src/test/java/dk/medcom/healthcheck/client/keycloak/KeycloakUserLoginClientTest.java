package dk.medcom.healthcheck.client.keycloak;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class KeycloakUserLoginClientTest {
    private static final String BASE_URL = "http://keycloak/realms/test/protocol/openid-connect";
    private static final String TOKEN_URL = BASE_URL + "/token";
    private static final String AUTH_URL = BASE_URL + "/auth";
    private static final String LOGOUT_URL = BASE_URL + "/logout";
    private static final String BROKER_IDP = "idp";
    private static final String IDP_LOGIN_URL = "http://keycloak/realms/test/broker/idp/login?client_id=client&tab_id=t1&session_code=abc";
    private static final String IDP_LOGIN_ACTION_URL = "http://keycloak/realms/idp/login-actions/authenticate?session_code=def&execution=e1&client_id=broker&tab_id=t2";
    private static final String BROKER_ENDPOINT_URL = "http://keycloak/realms/test/broker/idp/endpoint?state=s&code=idp-code";
    private static final String FIRST_BROKER_LOGIN_URL = "http://keycloak/realms/test/login-actions/first-broker-login?client_id=client&tab_id=t1";
    private static final String AFTER_FIRST_BROKER_LOGIN_URL = "http://keycloak/realms/test/broker/after-first-broker-login?client_id=client&tab_id=t1";
    private static final String REDIRECT_URI = "https://healthcheck/callback";

    private static final String LOGIN_PAGE = """
            <html><body>
            <form id="kc-form-login" action="http://keycloak/realms/test/login-actions/authenticate" method="post"></form>
            <div id="kc-social-providers">
              <ul><li><a id="zocial-other" href="http://keycloak/realms/test/broker/other/login">Other</a></li>
              <li><a id="zocial-idp" href="%s">IdP</a></li></ul>
            </div>
            </body></html>
            """.formatted(IDP_LOGIN_URL.replace("&", "&amp;"));

    private static final String IDP_LOGIN_PAGE = """
            <html><body>
            <form id="kc-form-login" action="%s" method="post">
              <input id="username" name="username" type="text">
              <input id="password" name="password" type="password">
              <button type="submit" id="kc-login" name="login">Sign In</button>
            </form>
            </body></html>
            """.formatted(IDP_LOGIN_ACTION_URL.replace("&", "&amp;"));

    private static final String CONTINUE_PAGE = """
            <html><body>
            <form id="kc-update-profile-form" action="%s" method="post">
              <input name="username" type="text" value="user">
              <button type="submit">Submit</button>
            </form>
            </body></html>
            """;

    private final FakeBrowserConnection browser = new FakeBrowserConnection();
    private final FakeKeycloak keycloak = new FakeKeycloak();

    @Test
    public void testLoginOk() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?state=x&session_state=s1&code=the-code");
        keycloak.respondJson(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh&1\",\"token_type\":\"Bearer\"}");
        keycloak.respondJson(204, null);

        var result = client("secret").login();

        assertTrue(result.ok(), result.message());
        assertNull(result.message());
        assertEquals(4, browser.requests.size());
        assertEquals(2, keycloak.requests.size());

        // Authorization request
        var authRequest = browser.requests.get(0);
        assertEquals(HttpMethod.GET, authRequest.getHttpMethod());
        assertTrue(authRequest.getUrl().toString().startsWith(AUTH_URL + "?"), authRequest.getUrl().toString());
        assertEquals("client", query(authRequest, "client_id"));
        assertEquals("code", query(authRequest, "response_type"));
        assertEquals("openid", query(authRequest, "scope"));
        assertEquals(REDIRECT_URI, query(authRequest, "redirect_uri"));
        assertEquals("S256", query(authRequest, "code_challenge_method"));

        // IdP button is clicked.
        assertEquals(IDP_LOGIN_URL, browser.requests.get(1).getUrl().toString());

        // IdP login form is posted.
        var loginRequest = browser.requests.get(2);
        assertEquals(HttpMethod.POST, loginRequest.getHttpMethod());
        assertEquals(IDP_LOGIN_ACTION_URL, loginRequest.getUrl().toString());
        assertEquals("user", parameter(loginRequest, "username"));
        assertEquals("p&ss", parameter(loginRequest, "password"));

        // Redirect back to the broker realm is followed.
        assertEquals(BROKER_ENDPOINT_URL, browser.requests.get(3).getUrl().toString());

        // Code is exchanged with the code verifier matching the code challenge.
        var tokenRequest = keycloak.requests.get(0);
        assertEquals(TOKEN_URL, tokenRequest.url().toString());
        var tokenForm = UriComponentsBuilder.fromUriString("?" + FakeKeycloak.body(tokenRequest)).build().getQueryParams();
        assertEquals("client", tokenForm.getFirst("client_id"));
        assertEquals("secret", tokenForm.getFirst("client_secret"));
        assertEquals("authorization_code", tokenForm.getFirst("grant_type"));
        assertEquals("the-code", tokenForm.getFirst("code"));
        assertEquals(REDIRECT_URI, decode(tokenForm.getFirst("redirect_uri")));
        var codeVerifier = tokenForm.getFirst("code_verifier");
        assertNotNull(codeVerifier);
        assertEquals(KeycloakUserLoginClient.generateCodeChallenge(codeVerifier), query(authRequest, "code_challenge"));

        // Session is ended.
        var logoutRequest = keycloak.requests.get(1);
        assertEquals(LOGOUT_URL, logoutRequest.url().toString());
        assertEquals("client_id=client&client_secret=secret&refresh_token=refresh%261", FakeKeycloak.body(logoutRequest));
    }

    @Test
    public void testFirstBrokerLogin() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(FIRST_BROKER_LOGIN_URL);
        browser.respondHtml(200, CONTINUE_PAGE.formatted(FIRST_BROKER_LOGIN_URL.replace("&", "&amp;")));
        browser.respondRedirect(AFTER_FIRST_BROKER_LOGIN_URL);
        browser.respondRedirect(REDIRECT_URI + "?state=x&code=first-code");
        keycloak.respondJson(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        keycloak.respondJson(204, null);

        var result = client("secret").login();

        assertTrue(result.ok(), result.message());
        assertEquals(7, browser.requests.size());
        assertEquals(FIRST_BROKER_LOGIN_URL, browser.requests.get(4).getUrl().toString());
        assertEquals(HttpMethod.POST, browser.requests.get(5).getHttpMethod());
        assertEquals(FIRST_BROKER_LOGIN_URL, browser.requests.get(5).getUrl().toString());
        assertEquals(AFTER_FIRST_BROKER_LOGIN_URL, browser.requests.get(6).getUrl().toString());
        assertTrue(FakeKeycloak.body(keycloak.requests.get(0)).contains("code=first-code"));
    }

    @Test
    public void testPublicClientSendsNoSecret() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?code=code&state=x");
        keycloak.respondJson(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        keycloak.respondJson(204, null);

        assertTrue(client(null).login().ok());
        assertFalse(FakeKeycloak.body(keycloak.requests.get(0)).contains("client_secret"));
        assertEquals("client_id=client&refresh_token=refresh", FakeKeycloak.body(keycloak.requests.get(1)));
    }

    @Test
    public void testIdpButtonNotFound() {
        browser.respondHtml(200, "<html><body><a id=\"zocial-other\" href=\"x\">Other</a></body></html>");

        var result = client("secret").login();

        assertFalse(result.ok());
        assertEquals("Login failed. IdP button not found.", result.message());
        assertEquals(1, browser.requests.size());
        assertTrue(keycloak.requests.isEmpty());
    }

    @Test
    public void testWrongPassword() {
        browser.respondHtml(200, LOGIN_PAGE);
        browser.respondHtml(200, IDP_LOGIN_PAGE);
        browser.respondHtml(200, IDP_LOGIN_PAGE.replace("</form>", "</form><span id=\"input-error\">Invalid username or password.</span>"));

        var result = client("secret").login();

        assertFalse(result.ok());
        assertTrue(keycloak.requests.isEmpty());
    }

    @Test
    public void testAuthorizationCodeNotFound() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?error=access_denied&state=x");

        var result = client("secret").login();

        assertFalse(result.ok());
        assertEquals("Login failed. Authorization code not found.", result.message());
        assertTrue(keycloak.requests.isEmpty());
    }

    @Test
    public void testTokenEndpointWrongStatus() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?code=code");
        keycloak.respondJson(400, "{\"error\":\"invalid_grant\"}");

        var result = client("secret").login();

        assertFalse(result.ok());
        assertEquals("Wrong HTTP status from Keycloak: 400", result.message());
    }

    @Test
    public void testNoRefreshToken() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?code=code");
        keycloak.respondJson(200, "{\"access_token\":\"abc\"}");

        var result = client("secret").login();

        assertFalse(result.ok());
        assertEquals("No refresh token in response from Keycloak. Can not logout.", result.message());
        assertEquals(1, keycloak.requests.size());
    }

    @Test
    public void testLogoutFails() {
        respondLoginUntilBrokerEndpoint();
        browser.respondRedirect(REDIRECT_URI + "?code=code");
        keycloak.respondJson(200, "{\"access_token\":\"abc\",\"refresh_token\":\"refresh\"}");
        keycloak.respondJson(400, "{\"error\":\"invalid_grant\"}");

        var result = client("secret").login();

        assertFalse(result.ok());
        assertEquals("Wrong HTTP status from Keycloak logout: 400", result.message());
    }

    @Test
    public void testTokenUrlDoesNotEndWithToken() {
        var client = new KeycloakUserLoginClient(browser::browser, keycloak.webClientBuilder(), KeycloakUserLoginClient.REQUEST_TIMEOUT, "http://keycloak/some/other/path", "client", "secret", REDIRECT_URI, BROKER_IDP, "user", "pass");

        var result = client.login();

        assertFalse(result.ok());
        assertEquals("Can not login to Keycloak. Token URL does not end with /token.", result.message());
        assertTrue(browser.requests.isEmpty());
        assertTrue(keycloak.requests.isEmpty());
    }

    private KeycloakUserLoginClient client(String clientSecret) {
        return new KeycloakUserLoginClient(browser::browser, keycloak.webClientBuilder(), KeycloakUserLoginClient.REQUEST_TIMEOUT, TOKEN_URL, "client", clientSecret, REDIRECT_URI, BROKER_IDP, "user", "p&ss");
    }

    /**
     * Login page with IdP button, IdP login page, and the redirect from the IdP back to the broker realm after login.
     */
    private void respondLoginUntilBrokerEndpoint() {
        browser.respondHtml(200, LOGIN_PAGE);
        browser.respondHtml(200, IDP_LOGIN_PAGE);
        browser.respondRedirect(BROKER_ENDPOINT_URL);
    }

    private static String query(WebRequest request, String name) {
        var value = UriComponentsBuilder.fromUri(URI.create(request.getUrl().toString())).build().getQueryParams().getFirst(name);
        return value == null ? null : decode(value);
    }

    private static String parameter(WebRequest request, String name) {
        return request.getRequestParameters().stream()
                .filter(p -> p.getName().equals(name))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
