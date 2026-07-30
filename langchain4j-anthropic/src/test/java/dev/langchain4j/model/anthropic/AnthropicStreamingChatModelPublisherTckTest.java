package dev.langchain4j.model.anthropic;

import static org.reactivestreams.FlowAdapters.toPublisher;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
 * {@code /sse/{n}/v1} streams {@code n} {@code text_delta} events, and {@code /fail/v1} returns a 500.
 * <p>
 * Anthropic relays its framing SSE (e.g. {@code content_block_start}) as provider {@code RawStreamingEvent}s whose
 * count the TCK cannot pin down, so the publisher under test is a thin {@code PartialResponse}-only view: the filter
 * forwards the real upstream subscription, so the model publisher's {@code onSubscribe} / demand / cancel / error /
 * complete contract is exercised end-to-end, while {@code n} text deltas yield exactly {@code n} elements.
 */
public class AnthropicStreamingChatModelPublisherTckTest extends PublisherVerification<ChatModelStreamingEvent> {

    private static final long DEFAULT_TIMEOUT_MILLIS = 2_000L;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;
    private static final long MAX_ELEMENTS = 100L;

    private static HttpServer server;
    private static String host;
    private static int port;

    public AnthropicStreamingChatModelPublisherTckTest() {
        super(
                new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @BeforeClass
    public static void startServer() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes(); // drain the POST body
            String path = exchange.getRequestURI().getPath(); // /sse/{n}/v1/messages or /fail/v1/messages
            if (path.startsWith("/fail")) {
                byte[] body = "{\"type\":\"error\",\"error\":{\"message\":\"boom\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
                return;
            }
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
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
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
                newModel("http://" + host + ":" + port + "/fail/v1").chat(request());
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
