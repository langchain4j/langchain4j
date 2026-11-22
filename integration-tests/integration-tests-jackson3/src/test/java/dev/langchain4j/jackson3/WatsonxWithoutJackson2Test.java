package dev.langchain4j.jackson3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of watsonx.ai with Jackson 2 absent from the classpath. The watsonx.ai SDK has its own JSON
 * binding, so this application excludes {@code com.ibm.watsonx:watsonx-ai-jackson2} from {@code langchain4j-watsonx}
 * and adds {@code com.ibm.watsonx:watsonx-ai-jackson3} instead, as the Jackson 3 guide describes. Real HTTP responses
 * from a local server go through the SDK, for both a chat call and a streaming call.
 */
class WatsonxWithoutJackson2Test {

    private static final String CHAT_RESPONSE =
            """
            {"id":"chatcmpl-1","object":"chat.completion","model_id":"openai/gpt-oss-120b","model":"openai/gpt-oss-120b",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!","reasoning":"Need to greet."},
               "finish_reason":"stop"}],
             "created":1790841833,"created_at":"2026-10-01T08:03:53.906Z",
             "usage":{"completion_tokens":24,"prompt_tokens":68,"total_tokens":92}}
            """;

    private static final String STREAMING_RESPONSE = sseEvent(
                    1, "{\"role\":\"assistant\",\"reasoning\":\"Need to greet.\"}", null, null)
            + sseEvent(2, "{\"content\":\"Hello!\"}", null, null)
            + sseEvent(
                    3,
                    "{\"content\":\"\"}",
                    "\"stop\"",
                    "{\"completion_tokens\":24,\"prompt_tokens\":68,\"total_tokens\":92}")
            + "event: close\n\n";

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ml/v1/text/chat", exchange -> respond(exchange, "application/json", CHAT_RESPONSE));
        server.createContext(
                "/ml/v1/text/chat_stream", exchange -> respond(exchange, "text/event-stream", STREAMING_RESPONSE));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void chats_through_the_jackson3_binding() {
        WatsonxChatModel chatModel = WatsonxChatModel.builder()
                .baseUrl(baseUrl())
                .authenticator(new FixedTokenAuthenticator())
                .projectId("project-id")
                .modelName("openai/gpt-oss-120b")
                .thinking(ThinkingEffort.LOW)
                .build();

        ChatResponse response = chatModel.chat(UserMessage.from("Hello"));

        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(response.aiMessage().thinking()).isEqualTo("Need to greet.");
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(92);
    }

    @Test
    void streams_through_the_jackson3_binding() throws Exception {
        WatsonxStreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(baseUrl())
                .authenticator(new FixedTokenAuthenticator())
                .projectId("project-id")
                .modelName("openai/gpt-oss-120b")
                .thinking(ThinkingEffort.LOW)
                .build();

        List<String> partialThinkings = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

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

        assertThat(partialThinkings).containsExactly("Need to greet.");
        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(response.aiMessage().thinking()).isEqualTo("Need to greet.");
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
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

    private static class FixedTokenAuthenticator implements Authenticator {

        @Override
        public String token() {
            return "token";
        }

        @Override
        public CompletableFuture<String> tokenAsync() {
            return CompletableFuture.completedFuture("token");
        }

        @Override
        public String scheme() {
            return "Bearer";
        }
    }
}
