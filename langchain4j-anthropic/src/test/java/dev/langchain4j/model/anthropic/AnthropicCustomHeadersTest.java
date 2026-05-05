package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicCustomHeadersTest {

    // language=json
    private static final String SUCCESS_RESPONSE = """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "content": [{"type": "text", "text": "Hello"}],
              "model": "claude-haiku-4-5-20251001",
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 5, "output_tokens": 3}
            }
            """;

    private HttpServer server;
    private AtomicReference<Map<String, String>> capturedHeaders;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        capturedHeaders = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            capturedHeaders.set(headers);

            byte[] body = SUCCESS_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void should_send_custom_headers_with_chat_model() {
        ChatModel model = AnthropicChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .customHeaders(Map.of("x-custom-header", "my-value", "x-tenant-id", "tenant-42"))
                .build();

        model.chat(UserMessage.from("Hi"));

        Map<String, String> headers = capturedHeaders.get();
        assertThat(headers).containsEntry("x-custom-header", "my-value");
        assertThat(headers).containsEntry("x-tenant-id", "tenant-42");
    }

    @Test
    void should_send_custom_headers_via_supplier_with_chat_model() {
        AtomicReference<String> dynamicValue = new AtomicReference<>("token-v1");

        ChatModel model = AnthropicChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .customHeaders(() -> Map.of("x-auth-token", dynamicValue.get()))
                .build();

        model.chat(UserMessage.from("Hi"));
        assertThat(capturedHeaders.get()).containsEntry("x-auth-token", "token-v1");

        dynamicValue.set("token-v2");
        model.chat(UserMessage.from("Hi again"));
        assertThat(capturedHeaders.get()).containsEntry("x-auth-token", "token-v2");
    }

    @Test
    void should_not_override_standard_headers() {
        ChatModel model = AnthropicChatModel.builder()
                .apiKey("my-real-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .customHeaders(Map.of("x-custom-header", "custom-value"))
                .build();

        model.chat(UserMessage.from("Hi"));

        Map<String, String> headers = capturedHeaders.get();
        assertThat(headers).containsEntry("x-custom-header", "custom-value");
        assertThat(headers).containsEntry("x-api-key", "my-real-key");
        assertThat(headers).containsKey("anthropic-version");
    }

    @Test
    void should_send_custom_headers_with_streaming_chat_model() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // language=json
        String sseResponse = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-haiku-4-5-20251001","stop_reason":null,"usage":{"input_tokens":5,"output_tokens":0}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}

                event: message_stop
                data: {"type":"message_stop"}

                """;

        server.removeContext("/v1/messages");
        server.createContext("/v1/messages", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            capturedHeaders.set(headers);

            byte[] body = sseResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .customHeaders(Map.of("x-streaming-header", "streaming-value"))
                .build();

        model.chat(java.util.List.of(UserMessage.from("Hi")), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedHeaders.get()).containsEntry("x-streaming-header", "streaming-value");
    }
}
