package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class HttpClientTimeoutIT {

    private static final int WIREMOCK_PORT = 8083;

    protected abstract List<HttpClient> clients(Duration readTimeout);

    protected abstract Class<? extends Exception> expectedReadTimeoutRootCauseExceptionType();

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    // Wiremock does not allow simulating connection timeouts: https://stackoverflow.com/a/60192310/23757366

    @Test
    void should_timeout_on_read_sync() {

        // given
        int readTimeoutMillis = 250;

        for (HttpClient client : clients(Duration.ofMillis(readTimeoutMillis))) {

            wireMockServer.stubFor(
                    WireMock.get("/endpoint").willReturn(WireMock.aResponse().withFixedDelay(readTimeoutMillis * 2)));

            HttpRequest request = HttpRequest.builder()
                    .method(GET)
                    .url(String.format("http://localhost:%s/endpoint", WIREMOCK_PORT))
                    .build();

            // when-then
            assertThatThrownBy(() -> client.execute(request))
                    .isExactlyInstanceOf(TimeoutException.class)
                    .hasRootCauseExactlyInstanceOf(expectedReadTimeoutRootCauseExceptionType())
                    .hasMessageContainingAll("time", "out");
        }
    }

    @Test
    void should_timeout_on_read_async() throws Exception {

        // given
        int readTimeoutMillis = 250;

        for (HttpClient client : clients(Duration.ofMillis(readTimeoutMillis))) {

            wireMockServer.stubFor(
                    WireMock.get("/endpoint").willReturn(WireMock.aResponse().withFixedDelay(readTimeoutMillis * 2)));

            HttpRequest request = HttpRequest.builder()
                    .method(GET)
                    .url(String.format("http://localhost:%s/endpoint", WIREMOCK_PORT))
                    .build();

            // when
            record StreamingResult(Throwable throwable, Set<Thread> threads) {}

            CompletableFuture<StreamingResult> completableFuture = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                private final Set<Thread> threads = new HashSet<>();

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    completableFuture.completeExceptionally(new IllegalStateException("onOpen() should not be called"));
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    completableFuture.completeExceptionally(
                            new IllegalStateException("onEvent() should not be called"));
                }

                @Override
                public void onError(Throwable throwable) {
                    threads.add(Thread.currentThread());
                    completableFuture.complete(new StreamingResult(throwable, threads));
                }

                @Override
                public void onClose() {
                    completableFuture.completeExceptionally(
                            new IllegalStateException("onClose() should not be called"));
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);

            // then
            StreamingResult streamingResult = completableFuture.get(readTimeoutMillis * 3, MILLISECONDS);

            assertThat(streamingResult.throwable())
                    .isExactlyInstanceOf(TimeoutException.class)
                    .hasRootCauseExactlyInstanceOf(expectedReadTimeoutRootCauseExceptionType())
                    .hasMessageContainingAll("time", "out");

            assertThat(streamingResult.threads()).hasSize(1);
            assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());

            verify(spyListener, times(1)).onError(any());
            verifyNoMoreInteractions(spyListener);
        }
    }
}
