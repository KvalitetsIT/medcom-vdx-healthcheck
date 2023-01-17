package dk.medcom.healthcheck.configuration;

import dk.medcom.healthcheck.client.security.*;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClient;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClientImpl;
import dk.medcom.healthcheck.client.videoapi.VideoApiClient;
import dk.medcom.healthcheck.client.videoapi.VideoApiClientImpl;
import dk.medcom.healthcheck.service.HealthcheckService;
import dk.medcom.healthcheck.service.HealthcheckServiceImpl;
import dk.medcom.healthcheck.service.HealthcheckServiceMetricImpl;
import dk.medcom.healthcheck.service.HealthcheckServiceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.cxf.Bus;
import org.apache.cxf.ws.security.trust.STSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ImportResource({"classpath:META-INF/cxf/cxf.xml"})
public class HealthcheckConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckConfiguration.class);

    @Bean
    public HealthcheckService healthcheckService(StsClient stsClient, ShortLinkClient shortLinkClient, AuthorizationClient authorizationClient, VideoApiClient videoApiClient) {
        return new HealthcheckServiceImpl(stsClient, shortLinkClient, authorizationClient, videoApiClient, new TokenEncoder());
    }

    @Bean
    public HealthcheckServiceMetrics healthcheckServiceMetricImpl(HealthcheckService healthcheckService, MeterRegistry meterRegistry) {
        return new HealthcheckServiceMetricImpl(healthcheckService, meterRegistry);
    }

    @Bean
    public VideoApiClient videoApiClient(WebClient.Builder webClientBuilder, @Value("${VIDEOAPI_ENDPOINT}") String videoApiEndpoint, KeyStore keyStore, @Value("${STS_STORE_PASSWORD}")String keystorePassword) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new VideoApiClientImpl(enhanceForMTls(webClientBuilder, keyStore, keystorePassword), videoApiEndpoint);
    }

    private WebClient.Builder enhanceForMTls(WebClient.Builder webClientBuilder, KeyStore keyStore, String password) throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        KeyManager[] managers = kmf.getKeyManagers();

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(managers, null, SecureRandom.getInstanceStrong());

        var httpClient = reactor.netty.http.client.HttpClient.create().secure(sslSpec -> {
            try {
                sslSpec.sslContext(SslContextBuilder.forClient().keyManager(kmf).build());
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        });

        return webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public AuthorizationClient authorizationClient(@Value("${VIDEOAPI_ENDPOINT}") String videoApiEndpoint, WebClient.Builder webClientBuilder, KeyStore keyStore, @Value("${STS_STORE_PASSWORD}")String keystorePassword) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return new AuthorizationClientImpl(videoApiEndpoint + "/token", enhanceForMTls(webClientBuilder, keyStore, keystorePassword));
    }

    @Bean
    public ShortLinkClient shortLinkClient(HttpClient httpClient) {
        return new ShortLinkClientImpl(httpClient);
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public StsClient vdxStsClient(STSClient stsClient) {
        return new StsClientImpl(stsClient, "urn:medcom:videoapi");
    }

    @Bean
    public STSClient stsClient(@Value("${STS_URL_WSDL}")String stsWsdlUrl, ApplicationContext applicationContext, @Value("${STS_PROPERTIES}")String propertyLocation) {
        logger.info("STS WSDL url: " + stsWsdlUrl);
        Bus bus = (Bus) applicationContext.getBean(Bus.DEFAULT_BUS_ID);
        STSClient stsClient = new STSClient(bus);

        stsClient.setWsdlLocation(stsWsdlUrl);
        stsClient.setServiceName("{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}SecurityTokenService");
        stsClient.setEndpointName("{http://docs.oasis-open.org/ws-sx/ws-trust/200512/}STS_Port");
        stsClient.setTokenType("http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0");
        stsClient.setKeyType("http://docs.oasis-open.org/ws-sx/ws-trust/200512/PublicKey");

        Map<String, Object> properties = new HashMap<>();
        properties.put("ws-security.signature.properties", propertyLocation);
        properties.put("ws-security.sts.token.properties", propertyLocation);

        stsClient.setProperties(properties);

        return stsClient;
    }

    @Bean
    public KeyStore getKeyStore(@Value("${STS_STORE}")String keystore, @Value("${STS_STORE_PASSWORD}")String keystorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (var keystoreInputStream = new FileInputStream(keystore)) {
            ks.load(keystoreInputStream, keystorePassword.toCharArray());
        }

        return ks;
    }
}
