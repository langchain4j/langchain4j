package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicBatchChatModelTest {

    private static final String CREATE_RESPONSE = """
            {"id":"msgbatch_1","type":"message_batch","processing_status":"in_progress",
             "request_counts":{"processing":2,"succeeded":0,"errored":0,"canceled":0,"expired":0}}
            """;

    private static final String ENDED_RESPONSE = """
            {"id":"msgbatch_1","type":"message_batch","processing_status":"ended",
             "results_url":"https://example.com/results",
             "request_counts":{"processing":0,"succeeded":1,"errored":1,"canceled":0,"expired":0}}
            """;

    private static final String RESULTS_JSONL =
            "{\"custom_id\":\"request-1\",\"result\":{\"type\":\"errored\",\"error\":{\"type\":\"error\","
                    + "\"error\":{\"type\":\"invalid_request_error\",\"message\":\"boom\"}}}}\n"
                    + "{\"custom_id\":\"request-0\",\"result\":{\"type\":\"succeeded\",\"message\":{\"id\":\"msg_0\","
                    + "\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"A\"}],"
                    + "\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":\"end_turn\","
                    + "\"usage\":{\"input_tokens\":5,\"output_tokens\":1}}}}\n";

    private static final String IN_PROGRESS_RESPONSE = """
            {"id":"msgbatch_running","type":"message_batch","processing_status":"in_progress",
             "request_counts":{"processing":2,"succeeded":0,"errored":0,"canceled":0,"expired":0}}
            """;

    private static final String CANCELED_RESULTS_JSONL =
            "{\"custom_id\":\"request-0\",\"result\":{\"type\":\"canceled\"}}\n";

    private static final String CANCELING_RESPONSE = """
            {"id":"msgbatch_1","type":"message_batch","processing_status":"canceling",
             "request_counts":{"processing":2,"succeeded":0,"errored":0,"canceled":0,"expired":0}}
            """;

    private static final String LIST_RESPONSE = """
            {"data":[{"id":"msgbatch_1","type":"message_batch","processing_status":"ended",
             "request_counts":{"processing":0,"succeeded":2,"errored":0,"canceled":0,"expired":0}}],
             "has_more":true,"first_id":"msgbatch_1","last_id":"msgbatch_9"}
            """;

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedCreateBody = new AtomicReference<>();
    private final AtomicReference<String> capturedListQuery = new AtomicReference<>();
    private final AtomicBoolean cancelCalled = new AtomicBoolean(false);
    private final AtomicBoolean resultsCalled = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/v1/messages/batches", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String responseBody;
            if (path.endsWith("/cancel")) {
                cancelCalled.set(true);
                responseBody = CANCELING_RESPONSE;
            } else if (path.endsWith("/results")) {
                resultsCalled.set(true);
                responseBody = path.contains("msgbatch_canceled") ? CANCELED_RESULTS_JSONL : RESULTS_JSONL;
            } else if (path.equals("/v1/messages/batches") && method.equals("POST")) {
                capturedCreateBody.set(read(exchange.getRequestBody()));
                responseBody = CREATE_RESPONSE;
            } else if (path.equals("/v1/messages/batches")) {
                capturedListQuery.set(exchange.getRequestURI().getQuery());
                responseBody = LIST_RESPONSE;
            } else if (path.endsWith("msgbatch_running")) {
                responseBody = IN_PROGRESS_RESPONSE;
            } else {
                responseBody = ENDED_RESPONSE;
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
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

    private AnthropicBatchChatModel model() {
        return AnthropicBatchChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("claude-haiku-4-5-20251001")
                .maxTokens(16)
                .build();
    }

    @Test
    void submit_assigns_ordered_custom_ids_and_returns_running_state() {
        BatchResponse<ChatResponse> response = model().submit(new BatchRequest<>(List.of(
                ChatRequest.builder().messages(UserMessage.from("first")).build(),
                ChatRequest.builder().messages(UserMessage.from("second")).build())));

        assertThat(response.batchId()).isEqualTo("msgbatch_1");
        assertThat(response.state()).isEqualTo(BatchState.RUNNING);
        assertThat(response.results()).isEmpty();
        assertThat(capturedCreateBody.get()).contains("\"custom_id\" : \"request-0\"", "\"custom_id\" : \"request-1\"");
    }

    @Test
    void retrieve_reorders_results_to_submission_order_and_maps_success_and_error() {
        BatchResponse<ChatResponse> response = model().retrieve("msgbatch_1");

        assertThat(response.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(response.results()).hasSize(2);

        BatchItemResult<ChatResponse> first = response.results().get(0);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.response().aiMessage().text()).isEqualTo("A");

        BatchItemResult<ChatResponse> second = response.results().get(1);
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.error().message()).isEqualTo("boom");

        assertThat(response.responses()).hasSize(1);
        assertThat(response.errors()).hasSize(1);
    }

    @Test
    void cancel_calls_the_cancel_endpoint() {
        model().cancel("msgbatch_1");
        assertThat(cancelCalled).isTrue();
    }

    @Test
    void list_maps_pagination_cursor_from_has_more_and_last_id() {
        BatchPage<ChatResponse> page = model().list(new BatchPagination(10, null));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(page.nextPageToken()).isEqualTo("msgbatch_9");
    }

    @Test
    void retrieve_while_in_progress_returns_running_and_does_not_fetch_results() {
        BatchResponse<ChatResponse> response = model().retrieve("msgbatch_running");

        assertThat(response.state()).isEqualTo(BatchState.RUNNING);
        assertThat(response.results()).isEmpty();
        assertThat(resultsCalled).isFalse();
    }

    @Test
    void retrieve_maps_canceled_result_without_error_to_failure_with_type_as_message() {
        BatchResponse<ChatResponse> response = model().retrieve("msgbatch_canceled");

        assertThat(response.results()).hasSize(1);
        BatchItemResult<ChatResponse> result = response.results().get(0);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().message()).isEqualTo("canceled");
    }

    @Test
    void list_with_null_pagination_sends_no_query_params_and_returns_batches() {
        BatchPage<ChatResponse> page = model().list(null);

        assertThat(page.batches()).hasSize(1);
        assertThat(capturedListQuery.get()).isNull();
    }

    private static String read(InputStream inputStream) throws IOException {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
