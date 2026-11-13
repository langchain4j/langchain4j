package dev.langchain4j.model.watsonx;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Sends real HTTP responses through the watsonx.ai SDK, so the JSON mapping of the model's reasoning is tested end to
 * end. Depending on the backend version, watsonx.ai returns the reasoning in {@code reasoning} or in
 * {@code reasoning_content}; both must end up in {@link AiMessage#thinking()}.
 */
class WatsonxReasoningFieldTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reasoning", "reasoning_content"})
    void should_return_thinking(String reasoningField) throws IOException {

        startServer("/ml/v1/text/chat", "application/json", chatResponse(reasoningField));

        WatsonxChatModel chatModel = WatsonxChatModel.builder()
                .baseUrl(baseUrl())
                .authenticator(authenticator())
                .projectId("project-id")
                .modelName("openai/gpt-oss-120b")
                .thinking(ThinkingEffort.LOW)
                .build();

        ChatResponse response = chatModel.chat(UserMessage.from("Hello"));

        assertThat(response.aiMessage().text()).isEqualTo("Hello! How can I help you today?");
        assertThat(response.aiMessage().thinking()).isEqualTo("Need to respond greeting.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reasoning", "reasoning_content"})
    void should_stream_thinking(String reasoningField) throws Exception {

        startServer("/ml/v1/text/chat_stream", "text/event-stream", streamingResponse(reasoningField));

        WatsonxStreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(baseUrl())
                .authenticator(authenticator())
                .projectId("project-id")
                .modelName("openai/gpt-oss-120b")
                .thinking(ThinkingEffort.LOW)
                .build();

        List<String> partialResponses = new CopyOnWriteArrayList<>();
        List<String> partialThinkings = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.add(partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                partialThinkings.add(partialThinking.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(10, SECONDS);

        assertThat(partialThinkings).containsExactly("Need to ", "respond greeting.");
        assertThat(String.join("", partialResponses)).isEqualTo("Hello!");
        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(response.aiMessage().thinking()).isEqualTo("Need to respond greeting.");
    }

    private void startServer(String path, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> respond(exchange, contentType, body));
        server.start();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static Authenticator authenticator() {
        Authenticator authenticator = mock(Authenticator.class);
        when(authenticator.token()).thenReturn("token");
        when(authenticator.tokenAsync()).thenReturn(CompletableFuture.completedFuture("token"));
        when(authenticator.scheme()).thenReturn("Bearer");
        return authenticator;
    }

    private static String chatResponse(String reasoningField) {
        return """
                {
                  "id": "chatcmpl-1",
                  "object": "chat.completion",
                  "model_id": "openai/gpt-oss-120b",
                  "model": "openai/gpt-oss-120b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello! How can I help you today?",
                        "%s": "Need to respond greeting."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "created": 1790841833,
                  "created_at": "2026-10-01T08:03:53.906Z",
                  "usage": {
                    "completion_tokens": 24,
                    "prompt_tokens": 68,
                    "total_tokens": 92
                  }
                }"""
                .formatted(reasoningField);
    }

    private static String streamingResponse(String reasoningField) {
        return sseEvent(1, "{\"role\":\"assistant\",\"%s\":\"Need to \"}".formatted(reasoningField), null, null)
                + sseEvent(2, "{\"%s\":\"respond greeting.\"}".formatted(reasoningField), null, null)
                + sseEvent(3, "{\"content\":\"Hello!\"}", null, null)
                + sseEvent(
                        4,
                        "{\"content\":\"\"}",
                        "\"stop\"",
                        "{\"completion_tokens\":24,\"prompt_tokens\":68,\"total_tokens\":92}")
                + "event: close\n\n";
    }

    private static String sseEvent(int id, String delta, String finishReason, String usage) {
        return "id: " + id + "\nevent: message\ndata: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                + "\"model_id\":\"openai/gpt-oss-120b\",\"model\":\"openai/gpt-oss-120b\","
                + "\"choices\":[{\"index\":0,\"finish_reason\":" + finishReason + ",\"delta\":" + delta + "}],"
                + "\"created\":1790841833,\"created_at\":\"2026-10-01T08:03:53.906Z\""
                + (usage == null ? "" : ",\"usage\":" + usage) + "}\n\n";
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
