package dev.langchain4j.model.anthropic;

import static org.reactivestreams.FlowAdapters.toPublisher;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Reactive Streams TCK for the Anthropic streaming publisher
 * ({@link AnthropicStreamingChatModel#chat(ChatRequest)} over {@code HttpClient.stream()}). A loopback
 * {@link HttpServer} serves deterministic Anthropic-style SSE, parameterized by the request path: a request to
 * {@code /sse/{n}/v1} streams {@code n} {@code text_delta} events. The error case ({@code createFailedPublisher})
 * points the model at a separate socket that accepts a connection and immediately closes it, so the request fails
 * at the transport level (connection reset) — fast and deterministic on every JDK, and independent of how a given
 * JDK's {@code HttpClient} drains an HTTP error-response body.
 * <p>
 * Anthropic relays its framing SSE (e.g. {@code content_block_start}) as provider {@code RawStreamingEvent}s whose
 * count the TCK cannot pin down, so the publisher under test is a thin {@code PartialResponse}-only view: the filter
 * forwards the real upstream subscription, so the model publisher's {@code onSubscribe} / demand / cancel / error /
 * complete contract is exercised end-to-end, while {@code n} text deltas yield exactly {@code n} elements.
 */
public class AnthropicStreamingChatModelPublisherTckTest extends PublisherVerification<ChatModelStreamingEvent> {

    // Every subscription performs a real HTTP round-trip to the loopback server before the first item can be
    // emitted, so the budget for *expected* signals must accommodate connection setup and cold-start latency on a
    // loaded CI runner. This timeout only adds slack: fast signals return immediately, so passing tests are
    // unaffected and only genuinely-stuck publishers ever wait this long.
    private static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;
    // Kept tight, independent of the receive timeout, so the "no signal must arrive" assertions stay fast.
    private static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = 2_000L;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;
    private static final long MAX_ELEMENTS = 100L;

    private static HttpServer server;
    private static String host;
    private static int port;
    private static ServerSocket resetServer;
    private static int resetPort;

    public AnthropicStreamingChatModelPublisherTckTest() {
        super(
                new TestEnvironment(
                        DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startServer() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes(); // drain the POST body
            String path = exchange.getRequestURI().getPath(); // /sse/{n}/v1/messages
            int deltas = Integer.parseInt(path.split("/")[2]);
            byte[] body = sse(deltas).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        host = loopback.getHostAddress();
        port = server.getAddress().getPort();

        // Accepts a connection then immediately closes it, so a request fails with a connection reset - a fast,
        // deterministic failure for createFailedPublisher that does not depend on HTTP error-body handling.
        resetServer = new ServerSocket(0, 50, loopback);
        resetPort = resetServer.getLocalPort();
        Thread resetThread = new Thread(
                () -> {
                    while (!resetServer.isClosed()) {
                        try (Socket socket = resetServer.accept()) {
                            socket.setSoLinger(true, 0); // send a RST rather than a graceful close
                        } catch (IOException ignored) {
                            return; // socket closed during teardown
                        }
                    }
                },
                "anthropic-tck-reset");
        resetThread.setDaemon(true);
        resetThread.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (resetServer != null) {
            try {
                resetServer.close();
            } catch (IOException ignored) {
                // best effort
            }
            resetServer = null;
        }
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createPublisher(long elements) {
        int deltas = (int) elements; // each text_delta -> one PartialResponse element
        Flow.Publisher<ChatModelStreamingEvent> stream =
                newModel("http://" + host + ":" + port + "/sse/" + deltas + "/v1").chat(request());
        return toPublisher(partialResponsesOnly(stream));
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createFailedPublisher() {
        Flow.Publisher<ChatModelStreamingEvent> stream =
                newModel("http://" + host + ":" + resetPort + "/v1").chat(request());
        return toPublisher(partialResponsesOnly(stream));
    }

    /**
     * A demand-preserving filter that forwards only {@link PartialResponse} events (dropping the provider framing
     * {@code RawStreamingEvent}s and the terminal aggregated response). It hands the real upstream subscription
     * straight to the downstream subscriber, so demand and cancellation reach the model publisher directly.
     */
    private static Flow.Publisher<ChatModelStreamingEvent> partialResponsesOnly(
            Flow.Publisher<ChatModelStreamingEvent> source) {
        return downstream -> source.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                downstream.onSubscribe(s);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                if (event instanceof PartialResponse) {
                    downstream.onNext(event);
                } else {
                    subscription.request(1); // dropped a non-text event; top up demand
                }
            }

            @Override
            public void onError(Throwable error) {
                downstream.onError(error);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        });
    }

    private static StreamingChatModel newModel(String baseUrl) {
        return AnthropicStreamingChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(16)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private static String sse(int deltas) {
        StringBuilder sb = new StringBuilder();
        sb.append("event: message_start\n")
                .append("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\","
                        + "\"role\":\"assistant\",\"content\":[],\"model\":\"claude-haiku-4-5-20251001\","
                        + "\"stop_reason\":null,\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}\n\n");
        sb.append("event: content_block_start\n")
                .append("data: {\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n");
        for (int i = 0; i < deltas; i++) {
            sb.append("event: content_block_delta\n")
                    .append("data: {\"type\":\"content_block_delta\",\"index\":0,"
                            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"x\"}}\n\n");
        }
        sb.append("event: content_block_stop\n")
                .append("data: {\"type\":\"content_block_stop\",\"index\":0}\n\n");
        sb.append("event: message_delta\n")
                .append("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},"
                        + "\"usage\":{\"output_tokens\":2}}\n\n");
        sb.append("event: message_stop\n").append("data: {\"type\":\"message_stop\"}\n\n");
        return sb.toString();
    }
}
