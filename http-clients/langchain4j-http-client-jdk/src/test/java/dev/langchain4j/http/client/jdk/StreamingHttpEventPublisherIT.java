package dev.langchain4j.http.client.jdk;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.reactivestreams.FlowAdapters.toPublisher;

// TODO move to the core http module?
public class StreamingHttpEventPublisherIT extends PublisherVerification<StreamingHttpEvent> {

    private static final long DEFAULT_TIMEOUT_MILLIS = 2_000L;
    private static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = DEFAULT_TIMEOUT_MILLIS;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;

    private static final long MAX_ELEMENTS = 100L;

    private static final String FAIL_PATH = "/fail";

    private static WireMockServer wireMockServer;
    private static HttpClient jdkClient;

    public StreamingHttpEventPublisherIT() {
        super(
                new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        jdkClient = HttpClient.newHttpClient();

        wireMockServer.stubFor(post(FAIL_PATH).willReturn(aResponse()
                .withStatus(500)
                .withBody("boom")));
    }

    @AfterClass
    public static void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    @Override
    public long maxElementsFromPublisher() {
        // Our publisher emits SuccessfulHttpResponse + N SSE events, so total items = N + 1.
        // This cap is the largest 'elements' value the TCK will ask for (it clamps Long.MAX_VALUE to this).
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<StreamingHttpEvent> createPublisher(long elements) {
        // elements == total downstream items we must emit before onComplete.
        // Our publisher emits 1 SuccessfulHttpResponse first, then one ServerSentEvent per SSE block
        // from the response body. So stub the server to return (elements - 1) SSE blocks.
        long sseCount = Math.max(0, elements - 1);
        String path = "/sse/" + elements;

        wireMockServer.stubFor(post(path).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(generateSseBody(sseCount))));

        return newPublisher(path);
    }

    @Override
    public Publisher<StreamingHttpEvent> createFailedPublisher() {
        return newPublisher(FAIL_PATH);
    }

    private Publisher<StreamingHttpEvent> newPublisher(String path) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(String.format("http://localhost:%d%s", wireMockServer.port(), path))
                .body("{}")
                .build();

        java.net.http.HttpRequest jdkRequest = JdkHttpClient.builder().build().toJdkRequest(httpRequest);
        JdkHttpClient.StreamingHttpEventPublisher publisher =
                new JdkHttpClient.StreamingHttpEventPublisher(jdkClient, jdkRequest);
        return toPublisher(publisher);
    }

    private static String generateSseBody(long count) {
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < count; i++) {
            sb.append("data: event-").append(i).append("\n\n");
        }
        return sb.toString();
    }
}
