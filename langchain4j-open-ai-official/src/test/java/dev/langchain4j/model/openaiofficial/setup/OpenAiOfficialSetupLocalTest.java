package dev.langchain4j.model.openaiofficial.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.credential.BearerTokenCredential;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiOfficialSetupLocalTest {

    private static final String MODEL = "foundry-local-model";

    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;
    private String baseUrl;
    private boolean omitModel;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", this::respond);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sync_request_without_api_key_reaches_local_endpoint_without_auth_header() {
        baseUrl = baseUrl.replace("127.0.0.1", "localhost");
        OpenAIClient client = syncClient(null);

        var completion = client.chat().completions().create(params());

        assertEquals(MODEL, completion.model());
        assertEquals(
                "local sync", completion.choices().get(0).message().content().orElseThrow());
        assertNull(authorization.get());
    }

    @Test
    void streaming_request_without_api_key_reaches_local_endpoint_without_auth_header() throws InterruptedException {
        OpenAIClientAsync client = asyncClient(null);
        CountDownLatch done = new CountDownLatch(1);
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        AtomicReference<Optional<Throwable>> error = new AtomicReference<>();

        client.chat().completions().createStreaming(params()).subscribe(new AsyncStreamResponse.Handler<>() {
            @Override
            public void onNext(ChatCompletionChunk chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete(Optional<Throwable> throwable) {
                error.set(throwable);
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertFalse(error.get().isPresent());
        assertEquals(2, chunks.size());
        assertEquals(MODEL, chunks.get(0).model());
        assertNull(authorization.get());
    }

    @Test
    void synchronous_chat_model_without_api_key_uses_local_endpoint() {
        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(2))
                .maxRetries(0)
                .build();

        assertEquals("local sync", model.chat("hello"));
        assertNull(authorization.get());
    }

    @Test
    void streaming_chat_model_without_api_key_uses_local_endpoint() throws InterruptedException {
        OpenAiOfficialStreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(2))
                .maxRetries(0)
                .build();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ChatResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        model.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
                done.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(response.get());
        assertEquals("local stream", response.get().aiMessage().text());
        assertNull(authorization.get());
    }

    @Test
    void fixed_dummy_api_key_is_preserved() {
        syncClient("dummy-key").chat().completions().create(params());

        assertEquals("Bearer dummy-key", authorization.get());
    }

    @Test
    void explicit_fictional_api_key_is_preserved() {
        syncClient("explicit-test-key").chat().completions().create(params());

        assertEquals("Bearer explicit-test-key", authorization.get());
    }

    @Test
    void explicit_credential_is_preserved() {
        OpenAIClient client = OpenAiOfficialSetup.setupSyncClient(
                baseUrl,
                null,
                BearerTokenCredential.create("credential-token"),
                null,
                null,
                null,
                false,
                false,
                MODEL,
                Duration.ofSeconds(2),
                0,
                null,
                null);

        client.chat().completions().create(params());

        assertEquals("Bearer credential-token", authorization.get());
    }

    @Test
    void default_openai_endpoint_still_requires_credentials() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OpenAiOfficialSetup.setupSyncClient(
                        null, null, null, null, null, null, false, false, MODEL, Duration.ofSeconds(2), 0, null, null));

        assertTrue(error.getMessage().contains("At least one credential source must be specified"));
    }

    @Test
    void non_local_compatible_endpoint_still_requires_credentials() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OpenAiOfficialSetup.setupSyncClient(
                        "https://127.example.invalid/v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        MODEL,
                        Duration.ofSeconds(2),
                        0,
                        null,
                        null));

        assertTrue(error.getMessage().contains("At least one credential source must be specified"));
    }

    @Test
    void missing_model_is_a_separate_parse_failure_after_http_request() {
        omitModel = true;
        var completion = syncClient("dummy-key").chat().completions().create(params());

        RuntimeException error = assertThrows(RuntimeException.class, completion::model);

        assertNotNull(error);
        assertEquals("Bearer dummy-key", authorization.get());
    }

    private OpenAIClient syncClient(String apiKey) {
        return OpenAiOfficialSetup.setupSyncClient(
                baseUrl, apiKey, null, null, null, null, false, false, MODEL, Duration.ofSeconds(2), 0, null, null);
    }

    private OpenAIClientAsync asyncClient(String apiKey) {
        return OpenAiOfficialSetup.setupAsyncClient(
                baseUrl, apiKey, null, null, null, null, false, false, MODEL, Duration.ofSeconds(2), 0, null, null);
    }

    private ChatCompletionCreateParams params() {
        return ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addUserMessage("hello")
                .build();
    }

    private void respond(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        boolean streaming = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                .contains("\"stream\":true");
        byte[] body =
                (streaming ? streamingResponse() : completionResponse(omitModel)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String completionResponse(boolean omitModel) {
        String model = omitModel ? "" : "\"model\":\"foundry-local-model\",";
        return """
                {"id":"chatcmpl-local","object":"chat.completion","created":1730000000,%s
                 "choices":[{"index":0,"message":{"role":"assistant","content":"local sync"},
                 "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                """.formatted(model);
    }

    private static String streamingResponse() {
        return """
                data: {"id":"chatcmpl-local","object":"chat.completion.chunk","created":1730000000,"model":"foundry-local-model","choices":[{"index":0,"delta":{"role":"assistant","content":"local stream"},"finish_reason":null}]}

                data: {"id":"chatcmpl-local","object":"chat.completion.chunk","created":1730000000,"model":"foundry-local-model","choices":[],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}

                data: [DONE]

                """;
    }
}
