package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicUserAgentTest {

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
        // Bind to the loopback address rather than the wildcard: "localhost" can resolve to both ::1 and 127.0.0.1,
        // and the client may then dial an address the server is not reachable on.
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/v1/messages", exchange -> {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            capturedHeaders.set(headers);

            byte[] body = SUCCESS_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        baseUrl = "http://" + loopback.getHostAddress() + ":"
                + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void should_send_default_user_agent() {
        ChatModel model = AnthropicChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(baseUrl)
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .build();

        model.chat(UserMessage.from("Hi"));

        assertThat(capturedHeaders.get()).containsEntry("user-agent", "LangChain4j");
    }
}
