package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic test of the reactive publisher path ({@link StreamingChatModel#chat(ChatRequest)} returning a
 * {@code Publisher<StreamingEvent>}). A local HTTP server replays a canned Anthropic SSE stream so no API key or
 * network access is needed; the test asserts the publisher emits the streamed text and a terminal complete event.
 */
class AnthropicStreamingChatModelPublisherTest {

    // language=text
    private static final String SSE_RESPONSE =
            """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-haiku-4-5-20251001","stop_reason":null,"usage":{"input_tokens":5,"output_tokens":0}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" there"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}

            event: message_stop
            data: {"type":"message_stop"}

            """;

    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        // Bind to the loopback address rather than the wildcard: "localhost" can resolve to both ::1 and 127.0.0.1,
        // and the client may then dial an address the server is not reachable on.
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/v1/messages", exchange -> {
            byte[] body = SSE_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        baseUrl = "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void should_stream_events_through_reactive_publisher() throws Exception {
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .build();

        List<StreamingEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        model.chat(request).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });

        completed.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The concatenated partial-response text reproduces the streamed tokens...
        String streamedText = events.stream()
                .filter(event -> event instanceof PartialResponse)
                .map(event -> ((PartialResponse) event).text())
                .reduce("", String::concat);
        assertThat(streamedText).isEqualTo("Hi there");

        // ...and the terminal event carries the aggregated ChatResponse.
        StreamingEvent last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(CompleteResponse.class);
        assertThat(((CompleteResponse) last).chatResponse().aiMessage().text()).isEqualTo("Hi there");
    }
}
