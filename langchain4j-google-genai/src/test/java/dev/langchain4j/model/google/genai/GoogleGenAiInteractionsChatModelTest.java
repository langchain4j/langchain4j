package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleGenAiInteractionsChatModelTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastCustomHeader = new AtomicReference<>();
    private final AtomicInteger statusToReturn = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastCustomHeader.set(exchange.getRequestHeaders().getFirst("X-Test-Header"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            int status = statusToReturn.getAndSet(200);
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void should_default_to_v1_and_return_chat_response_with_metadata() {
        responseBody.set("""
                {
                  "model": "gemini-3.8-flash",
                  "status": "completed",
                  "steps": [
                    {
                      "type": "model_output",
                      "content": [{"type": "text", "text": "Namaste!"}]
                    }
                  ],
                  "usage": {
                    "total_input_tokens": 10,
                    "total_output_tokens": 5,
                    "total_tokens": 15
                  }
                }
                """);

        List<String> events = new ArrayList<>();
        ChatModelListener listener = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                events.add("request");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                events.add("response");
            }
        };

        GoogleGenAiInteractionsChatModel model = GoogleGenAiInteractionsChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-3.8-flash")
                .customHeaders(Map.of("X-Test-Header", "hello-interactions"))
                .listeners(List.of(listener))
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);

        ChatResponse response = model.chat(UserMessage.from("Hi"));
        assertThat(lastPath.get()).isEqualTo("/v1/interactions");
        assertThat(lastCustomHeader.get()).isEqualTo("hello-interactions");
        assertThat(lastBody.get()).contains("\"store\":false");
        assertThat(response.aiMessage().text()).isEqualTo("Namaste!");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata()).isInstanceOf(GoogleGenAiInteractionsChatResponseMetadata.class);
        assertThat(response.metadata().id()).isNull();
        assertThat(events).containsExactly("request", "response");
    }

    @Test
    void should_use_v1beta_for_labels_and_retry_on_server_error() {
        statusToReturn.set(503);
        responseBody.set("""
                {
                  "model": "gemini-3.8-flash",
                  "status": "completed",
                  "steps": [
                    {
                      "type": "model_output",
                      "content": [{"type": "text", "text": "Recovered"}]
                    }
                  ]
                }
                """);

        GoogleGenAiInteractionsChatModel model = GoogleGenAiInteractionsChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .apiVersion("v1beta")
                .modelName("gemini-3.8-flash")
                .labels(Map.of("team", "core"))
                .maxRetries(2)
                .build();

        ChatResponse response = model.chat(UserMessage.from("Ping"));
        assertThat(lastPath.get()).isEqualTo("/v1beta/interactions");
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(response.aiMessage().text()).isEqualTo("Recovered");
    }

    @Test
    void should_fail_before_http_call_on_unsupported_params_or_invalid_customizer() {
        GoogleGenAiInteractionsChatModel modelWithTopK = GoogleGenAiInteractionsChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-3.8-flash")
                .topK(10)
                .build();

        assertThatThrownBy(() -> modelWithTopK.chat(UserMessage.from("Hi")))
                .isInstanceOf(UnsupportedFeatureException.class);
        assertThat(requestCount.get()).isZero();

        GoogleGenAiInteractionsChatModel badCustomizerModel = GoogleGenAiInteractionsChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-3.8-flash")
                .createInteractionCustomizer(b -> b.store(true))
                .build();

        assertThatThrownBy(() -> badCustomizerModel.chat(UserMessage.from("Hi")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store=false");
        assertThat(requestCount.get()).isZero();
    }

    @Test
    void should_surface_v1_labels_error_message() {
        statusToReturn.set(400);
        responseBody.set("""
                {
                  "error": {
                    "code": 400,
                    "message": "The field 'labels' is only available in v1beta"
                  }
                }
                """);

        GoogleGenAiInteractionsChatModel model = GoogleGenAiInteractionsChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl)
                .modelName("gemini-3.8-flash")
                .labels(Map.of("team", "core"))
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> model.chat(
                        ChatRequest.builder().messages(UserMessage.from("Hi")).build()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("The field 'labels' is only available in v1beta");
    }
}
