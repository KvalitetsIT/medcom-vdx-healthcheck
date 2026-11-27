package dk.medcom.healthcheck.client.keycloak;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

/**
 * WebClient that records requests and answers them with queued responses.
 */
class FakeKeycloak {
    final List<ClientRequest> requests = new ArrayList<>();
    private final Queue<Function<ClientRequest, Mono<ClientResponse>>> responses = new LinkedList<>();

    WebClient.Builder webClientBuilder() {
        return WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return responses.remove().apply(request);
        });
    }

    void respond(Function<ClientRequest, Mono<ClientResponse>> response) {
        responses.add(response);
    }

    void respondJson(int status, String json) {
        respond(request -> {
            var builder = ClientResponse.create(HttpStatus.valueOf(status))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            if(json != null) {
                builder.body(json);
            }
            return Mono.just(builder.build());
        });
    }

    static String body(ClientRequest request) {
        var httpRequest = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(httpRequest, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return httpRequest.getBodyAsString().block();
    }
}
