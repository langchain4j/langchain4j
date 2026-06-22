package dev.langchain4j.http.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the cold-publisher contract of
 * {@link HttpClient#stream(HttpRequest)}: building the publisher sends nothing, and each
 * {@code subscribe()} initiates a new, independent request.
 */
public abstract class HttpClientPublisherIT {

    private static final String PATH = "/sse";

    protected abstract List<HttpClient> clients();

    private WireMockServer wireMockServer;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void publisher_is_cold_and_each_subscribe_initiates_a_new_request() throws Exception {
        for (HttpClient client : clients()) {
            wireMockServer.resetRequests();
            wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("data: one\n\ndata: two\n\n")));

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("http://localhost:" + wireMockServer.port() + PATH)
                    .body("{}")
                    .build();

            // Building the publisher is cold: no request is sent until someone subscribes.
            Flow.Publisher<StreamingHttpEvent> publisher = client.stream(request);
            wireMockServer.verify(0, postRequestedFor(urlEqualTo(PATH)));

            // First subscription -> one request, events delivered.
            assertThat(collectEvents(publisher)).isNotEmpty();

            // Second subscription to the SAME publisher instance -> a second, independent request.
            assertThat(collectEvents(publisher)).isNotEmpty();

            wireMockServer.verify(2, postRequestedFor(urlEqualTo(PATH)));
        }
    }

    private static List<ServerSentEvent> collectEvents(Flow.Publisher<StreamingHttpEvent> publisher) throws Exception {
        List<ServerSentEvent> events = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingHttpEvent item) {
                if (item instanceof ServerSentEvent event) {
                    events.add(event);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(30, SECONDS);
        return events;
    }
}
