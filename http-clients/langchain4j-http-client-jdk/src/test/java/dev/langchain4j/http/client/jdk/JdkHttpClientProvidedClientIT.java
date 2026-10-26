package dev.langchain4j.http.client.jdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpClientProvidedClientIT {

    private WireMockServer server;
    private HttpClient sharedClient;

    @BeforeEach
    void beforeEach() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        sharedClient =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void wrappers_sharing_a_real_client_should_apply_their_own_read_timeouts() {
        server.stubFor(get(urlEqualTo("/delayed"))
                .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("ok")));

        JdkHttpClient shortTimeoutClient = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .readTimeout(Duration.ofMillis(100))
                .build();
        JdkHttpClient longTimeoutClient = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .readTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = request("/delayed");

        assertThatThrownBy(() -> shortTimeoutClient.execute(request))
                .isExactlyInstanceOf(TimeoutException.class)
                .hasRootCauseInstanceOf(java.net.http.HttpTimeoutException.class);

        SuccessfulHttpResponse response = longTimeoutClient.execute(request);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ok");
    }

    @Test
    void wrappers_sharing_a_real_client_should_handle_streaming_success_and_failure() throws Exception {
        server.stubFor(get(urlEqualTo("/events"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: one\n\ndata: two\n\n")));
        server.stubFor(get(urlEqualTo("/events-error"))
                .willReturn(aResponse().withStatus(503).withBody("stream failed")));

        JdkHttpClient smallBufferClient = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .streamingBufferSize(1)
                .build();
        JdkHttpClient largerBufferClient = JdkHttpClient.builder()
                .httpClient(sharedClient)
                .streamingBufferSize(8)
                .build();

        assertThat(collectEvents(smallBufferClient.stream(request("/events"))))
                .extracting(ServerSentEvent::data)
                .containsExactly("one", "two");

        assertThatThrownBy(() -> collectEvents(largerBufferClient.stream(request("/events-error"))))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(HttpException.class)
                .hasRootCauseMessage("stream failed");
    }

    @Test
    void wrappers_sharing_a_real_client_should_reuse_its_http_1_1_connection() throws Exception {
        try (RecordingHttpServer recordingServer = new RecordingHttpServer()) {
            JdkHttpClient first =
                    JdkHttpClient.builder().httpClient(sharedClient).build();
            JdkHttpClient second =
                    JdkHttpClient.builder().httpClient(sharedClient).build();

            assertThat(bodyOf(first.execute(recordingServer.request("/reuse")))).isEqualTo("ok");
            assertThat(bodyOf(second.execute(recordingServer.request("/reuse"))))
                    .isEqualTo("ok");

            assertThat(recordingServer.remoteAddresses()).hasSize(2);
            assertThat(recordingServer.remoteAddresses())
                    .containsOnly(recordingServer.remoteAddresses().get(0));
        }
    }

    @Test
    void wrappers_sharing_a_real_client_should_support_concurrent_requests_and_remain_usable_after_a_timeout()
            throws Exception {
        try (RecordingHttpServer recordingServer = new RecordingHttpServer()) {
            JdkHttpClient shortTimeoutClient = JdkHttpClient.builder()
                    .httpClient(sharedClient)
                    .readTimeout(Duration.ofSeconds(1))
                    .build();
            JdkHttpClient longTimeoutClient = JdkHttpClient.builder()
                    .httpClient(sharedClient)
                    .readTimeout(Duration.ofSeconds(5))
                    .build();

            CompletableFuture<SuccessfulHttpResponse> slowRequest =
                    shortTimeoutClient.executeAsync(recordingServer.request("/concurrent-slow"));
            CompletableFuture<SuccessfulHttpResponse> fastRequest =
                    longTimeoutClient.executeAsync(recordingServer.request("/concurrent-fast"));

            assertThat(bodyOf(fastRequest.get(5, SECONDS))).isEqualTo("fast");
            assertThatThrownBy(() -> slowRequest.get(5, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
            assertThat(recordingServer.awaitConcurrentRequests()).isTrue();
            assertThat(recordingServer.maxActiveRequests()).isGreaterThanOrEqualTo(2);

            java.net.http.HttpResponse<String> response = sharedClient.send(
                    java.net.http.HttpRequest.newBuilder(URI.create(recordingServer.url("/after")))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("after");
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + server.port() + path)
                .build();
    }

    private static String bodyOf(SuccessfulHttpResponse response) {
        return response.body();
    }

    private static List<ServerSentEvent> collectEvents(Flow.Publisher<HttpStreamingEvent> publisher) throws Exception {
        List<ServerSentEvent> events = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpStreamingEvent item) {
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
        done.get(10, SECONDS);
        return events;
    }

    private static class RecordingHttpServer implements AutoCloseable {

        private final List<InetSocketAddress> remoteAddresses = new CopyOnWriteArrayList<>();
        private final CountDownLatch concurrentRequestsStarted = new CountDownLatch(2);
        private final CountDownLatch concurrentRequestsFinished = new CountDownLatch(2);
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxActiveRequests = new AtomicInteger();
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private RecordingHttpServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/reuse", exchange -> respond(exchange, "ok"));
            server.createContext("/after", exchange -> respond(exchange, "after"));
            server.createContext("/concurrent-fast", exchange -> respondConcurrently(exchange, "fast", false));
            server.createContext("/concurrent-slow", exchange -> respondConcurrently(exchange, "slow", true));
            server.start();
        }

        private HttpRequest request(String path) {
            return HttpRequest.builder().method(GET).url(url(path)).build();
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private void respondConcurrently(HttpExchange exchange, String body, boolean delay) throws IOException {
            int active = activeRequests.incrementAndGet();
            maxActiveRequests.accumulateAndGet(active, Math::max);
            concurrentRequestsStarted.countDown();
            try {
                if (!concurrentRequestsStarted.await(5, SECONDS)) {
                    respond(exchange, "requests did not overlap");
                    return;
                }
                if (delay) {
                    Thread.sleep(2000);
                }
                respond(exchange, body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } finally {
                activeRequests.decrementAndGet();
                concurrentRequestsFinished.countDown();
            }
        }

        private void respond(HttpExchange exchange, String body) throws IOException {
            remoteAddresses.add(exchange.getRemoteAddress());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private List<InetSocketAddress> remoteAddresses() {
            return remoteAddresses;
        }

        private boolean awaitConcurrentRequests() throws InterruptedException {
            return concurrentRequestsFinished.await(5, SECONDS);
        }

        private int maxActiveRequests() {
            return maxActiveRequests.get();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
