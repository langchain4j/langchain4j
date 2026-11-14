package dev.langchain4j.model.google.genai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks the headers that reach the server, through the real google-genai SDK HTTP client.
 */
class GoogleGenAiPerRequestCustomHeadersTest {

    private static final String RESPONSE =
            "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello!\"}]},\"finishReason\":\"STOP\"}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1,\"totalTokenCount\":2}}";

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    private static final ChatRequestParameters DEFAULT_PARAMETERS_WITH_HEADERS = ChatRequestParameters.builder()
            .httpParameters(HttpParameters.builder()
                    .customHeaders(Map.of("X-Default", "d", "X-Shared", "default"))
                    .build())
            .build();

    private static final ChatRequest REQUEST_WITH_SHARED_HEADER = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request"))
            .build();

    private final List<Headers> receivedHeaders = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            receivedHeaders.add(exchange.getRequestHeaders());
            boolean streaming = exchange.getRequestURI().toString().contains("streamGenerateContent");
            byte[] body = (streaming ? "data: " + RESPONSE + "\n\n" : RESPONSE).getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(singleRequestHeaders());
    }

    @Test
    void chat_model_should_send_per_request_headers_async() throws Exception {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chatAsync(CHAT_REQUEST).get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(singleRequestHeaders());
    }

    @Test
    void streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertPerRequestHeadersSent(singleRequestHeaders());
    }

    @Test
    void per_request_tracking_headers_should_be_appended_to_the_sdk_ones() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("User-Agent", "my-app/1.0", "X-Goog-Api-Client", "my-app/1.0"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        Headers headers = singleRequestHeaders();
        assertThat(headers.get("User-Agent")).singleElement().satisfies(userAgent -> assertThat(userAgent)
                .startsWith("google-genai-sdk/")
                .endsWith(" LangChain4j my-app/1.0"));
        assertThat(headers.get("X-Goog-Api-Client")).singleElement().satisfies(apiClient -> assertThat(apiClient)
                .startsWith("google-genai-sdk/")
                .endsWith(" my-app/1.0"));
    }

    @Test
    void streaming_chat_model_should_append_per_request_tracking_headers_to_the_sdk_ones() throws Exception {

        // given
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of(
                        "user-agent", "google-adk/1.2.0 gl-java/21",
                        "x-goog-api-client", "google-adk/1.2.0 gl-java/21"))
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(chatRequest, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        Headers headers = singleRequestHeaders();
        assertThat(headers.get("User-Agent")).singleElement().satisfies(userAgent -> assertThat(userAgent)
                .startsWith("google-genai-sdk/")
                .endsWith(" LangChain4j google-adk/1.2.0 gl-java/21"));
        assertThat(headers.get("X-Goog-Api-Client")).singleElement().satisfies(apiClient -> assertThat(apiClient)
                .startsWith("google-genai-sdk/")
                .endsWith(" google-adk/1.2.0 gl-java/21"));
    }

    @Test
    void should_send_langchain4j_user_agent_by_default() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .build();

        // when
        model.chat("Hello");

        // then
        assertThat(singleRequestHeaders().get("User-Agent"))
                .singleElement()
                .satisfies(userAgent -> assertThat(userAgent).startsWith("google-genai-sdk/").endsWith(" LangChain4j"));
    }

    @Test
    void user_agent_set_on_model_builder_should_replace_langchain4j_user_agent() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .customHeaders(Map.of("User-Agent", "my-app/1.0"))
                .build();

        // when
        model.chat("Hello");

        // then
        assertThat(singleRequestHeaders().get("User-Agent"))
                .singleElement()
                .satisfies(userAgent -> assertThat(userAgent)
                        .startsWith("google-genai-sdk/")
                        .endsWith(" my-app/1.0")
                        .doesNotContain("LangChain4j"));
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .build();

        // when
        model.chat(CHAT_REQUEST);
        model.chat("Hello again");

        // then
        assertThat(receivedHeaders).hasSize(2);
        assertThat(receivedHeaders.get(1).containsKey("X-Correlation-Id")).isFalse();
    }

    @Test
    void batch_chat_model_should_reject_per_request_headers() {

        // given
        GoogleGenAiBatchChatModel model = GoogleGenAiBatchChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .build();

        // when-then
        assertThatThrownBy(() -> model.submit("batch", List.of(CHAT_REQUEST)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        assertThat(receivedHeaders).isEmpty();
    }

    @Test
    void batch_chat_model_should_reject_per_request_headers_in_default_request_parameters_at_build() {

        assertThatThrownBy(() -> GoogleGenAiBatchChatModel.builder()
                        .apiKey("test-key")
                        .apiEndpoint(baseUrl())
                        .modelName("gemini-model")
                        .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        assertThat(receivedHeaders).isEmpty();
    }

    @Test
    void chat_model_should_send_headers_from_default_request_parameters() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertDefaultAndRequestHeadersSent(singleRequestHeaders());
    }

    @Test
    void streaming_chat_model_should_send_headers_from_default_request_parameters() throws Exception {

        // given
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .defaultRequestParameters(DEFAULT_PARAMETERS_WITH_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, handler(futureResponse));
        futureResponse.get(5, SECONDS);

        // then
        assertDefaultAndRequestHeadersSent(singleRequestHeaders());
    }

    @Test
    void per_request_api_key_header_should_not_replace_the_api_key_of_the_client() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .apiEndpoint(baseUrl())
                .modelName("gemini-model")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("x-goog-api-key", "request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        assertThat(singleRequestHeaders().get("X-Goog-Api-Key")).containsExactly("test-key");
    }

    private static void assertDefaultAndRequestHeadersSent(Headers headers) {
        assertThat(headers.get("X-Default")).containsExactly("d");
        assertThat(headers.get("X-Shared")).containsExactly("request");
    }

    private static void assertPerRequestHeadersSent(Headers headers) {
        assertThat(headers.get("X-Model-Header")).containsExactly("model");
        assertThat(headers.get("X-Shared")).containsExactly("request");
        assertThat(headers.get("X-Correlation-Id")).containsExactly("123");
        assertThat(headers.get("X-Goog-Api-Key")).containsExactly("test-key");
    }

    private Headers singleRequestHeaders() {
        assertThat(receivedHeaders).hasSize(1);
        return receivedHeaders.get(0);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> futureResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        };
    }
}
