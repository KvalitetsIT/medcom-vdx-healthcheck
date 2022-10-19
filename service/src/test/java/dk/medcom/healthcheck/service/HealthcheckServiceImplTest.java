package dk.medcom.healthcheck.service;

import dk.medcom.healthcheck.client.security.StsClient;
import dk.medcom.healthcheck.client.shortlink.ShortLinkClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HealthcheckServiceImplTest {
    private HealthcheckService healthcheckService;
    private ShortLinkClient shortLinkClient;
    private StsClient stsClient;

//    @Before
//    public void setup() {
//        stsClient = Mockito.mock(StsClient.class);
//        shortLinkClient = Mockito.mock(ShortLinkClient.class);
//        healthcheckService = new HealthcheckServiceImpl(stsClient, shortLinkClient);
//    }
//
//    @Test
//    public void testServiceUp() throws IOException, InterruptedException {
//        var input = new HelloServiceInput(UUID.randomUUID().toString());
//
//        var result = healthcheckService.checkHealth();
//        assertNotNull(result);
//
//        Mockito.verify(shortLinkClient, times(1)).getShortLink(Mockito.any());
//    }
}
