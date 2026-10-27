package dev.langchain4j.http.client.okhttp;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import okhttp3.Dispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OkHttpClientDoubleTerminationTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void should_call_onError_but_not_onClose_when_stream_is_truncated_mid_body() throws Exception {

        // given: a response that starts successfully (status 200, valid headers) but whose body
        // is corrupted mid-transfer, so the SSE parser reports an IOException via onError and returns
        // normally rather than throwing it back to the caller.
        wireMockServer.stubFor(WireMock.get("/endpoint")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: hello\n\n")
                        .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        List<String> calls = new CopyOnWriteArrayList<>();
        CountDownLatch terminated = new CountDownLatch(1);
        Dispatcher dispatcher = new Dispatcher();

        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + wireMockServer.port() + "/endpoint")
                .build();

        // when
        OkHttpClient.builder()
                .okHttpClientBuilder(new okhttp3.OkHttpClient.Builder().dispatcher(dispatcher))
                .build()
                .execute(request, new DefaultServerSentEventParser(), new ServerSentEventListener() {

                    @Override
                    public void onOpen(SuccessfulHttpResponse response) {
                        calls.add("onOpen");
                    }

                    @Override
                    public void onEvent(ServerSentEvent event) {
                        calls.add("onEvent");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        calls.add("onError");
                        terminated.countDown();
                    }

                    @Override
                    public void onClose() {
                        calls.add("onClose");
                        terminated.countDown();
                    }
                });

        // then
        assertThat(terminated.await(10, SECONDS)).isTrue();
        awaitDispatcherIdle(dispatcher);
        assertThat(calls).containsExactly("onOpen", "onError");
    }

    private static void awaitDispatcherIdle(Dispatcher dispatcher) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (dispatcher.runningCallsCount() > 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("OkHttp dispatcher did not go idle in time");
            }
            Thread.sleep(10);
        }
    }
}
