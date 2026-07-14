package dev.langchain4j.model.mistralai;

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

class MistralAiBatchChatModelTest {

    // language=json
    private static final String QUEUED_RESPONSE = """
            {"id":"batch_1","object":"batch","status":"QUEUED","total_requests":2}
            """;

    // language=json
    private static final String SUCCESS_RESPONSE = """
            {"id":"batch_1","object":"batch","status":"SUCCESS","output_file":"file_out_1",
             "total_requests":2,"succeeded_requests":1,"failed_requests":1}
            """;

    // language=json
    private static final String RUNNING_RESPONSE = """
            {"id":"batch_running","object":"batch","status":"RUNNING","total_requests":2}
            """;

    // language=json
    private static final String CANCEL_RESPONSE = """
            {"id":"batch_1","object":"batch","status":"CANCELLATION_REQUESTED"}
            """;

    // language=json
    private static final String LIST_RESPONSE = """
            {"object":"list","data":[{"id":"batch_1","status":"SUCCESS","output_file":"file_out_1"}],"total":25}
            """;

    private static final String RESULTS_JSONL = "{\"custom_id\":\"request-1\",\"error\":{\"message\":\"boom\"}}\n"
            + "{\"custom_id\":\"request-0\",\"response\":{\"status_code\":200,\"body\":{\"id\":\"cmpl_0\","
            + "\"model\":\"mistral-small-latest\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
            + "\"content\":[{\"type\":\"text\",\"text\":\"A\"}]},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}}}\n";

    private static final String STATUS_FAIL_JSONL =
            "{\"custom_id\":\"request-0\",\"response\":{\"status_code\":400,\"body\":{}}}\n";

    // language=json
    private static final String SUCCESS_WITH_ERRORS_RESPONSE = """
            {"id":"batch_witherrors","object":"batch","status":"SUCCESS","output_file":"file_out_1","error_file":"file_err_1"}
            """;

    private static final String ERROR_FILE_JSONL =
            "{\"custom_id\":\"request-2\",\"error\":{\"message\":\"rate limited\"}}\n";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedCreateBody = new AtomicReference<>();
    private final AtomicReference<String> capturedListQuery = new AtomicReference<>();
    private final AtomicBoolean cancelCalled = new AtomicBoolean(false);
    private final AtomicBoolean resultsFileFetched = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String responseBody;
            if (path.endsWith("/cancel")) {
                cancelCalled.set(true);
                responseBody = CANCEL_RESPONSE;
            } else if (path.startsWith("/v1/files/") && path.endsWith("/content")) {
                resultsFileFetched.set(true);
                if (path.contains("file_statusfail")) {
                    responseBody = STATUS_FAIL_JSONL;
                } else if (path.contains("file_err_1")) {
                    responseBody = ERROR_FILE_JSONL;
                } else {
                    responseBody = RESULTS_JSONL;
                }
            } else if (path.equals("/v1/batch/jobs") && method.equals("POST")) {
                capturedCreateBody.set(read(exchange.getRequestBody()));
                responseBody = QUEUED_RESPONSE;
            } else if (path.equals("/v1/batch/jobs")) {
                capturedListQuery.set(exchange.getRequestURI().getQuery());
                responseBody = LIST_RESPONSE;
            } else if (path.endsWith("batch_running")) {
                responseBody = RUNNING_RESPONSE;
            } else if (path.endsWith("batch_witherrors")) {
                responseBody = SUCCESS_WITH_ERRORS_RESPONSE;
            } else if (path.endsWith("batch_statusfail")) {
                responseBody = SUCCESS_RESPONSE.replace("file_out_1", "file_statusfail");
            } else {
                responseBody = SUCCESS_RESPONSE;
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

    private MistralAiBatchChatModel model() {
        return MistralAiBatchChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName("mistral-small-latest")
                .maxTokens(16)
                .build();
    }

    @Test
    void submit_assigns_ordered_custom_ids_and_returns_pending_state() {
        BatchResponse<ChatResponse> response = model().submit(new BatchRequest<>(List.of(
                ChatRequest.builder().messages(UserMessage.from("first")).build(),
                ChatRequest.builder().messages(UserMessage.from("second")).build())));

        assertThat(response.batchId()).isEqualTo("batch_1");
        assertThat(response.state()).isEqualTo(BatchState.PENDING);
        assertThat(response.results()).isEmpty();
        assertThat(capturedCreateBody.get())
                .contains("\"custom_id\" : \"request-0\"", "\"custom_id\" : \"request-1\"")
                .contains("\"endpoint\" : \"/v1/chat/completions\"")
                .contains("\"model\" : \"mistral-small-latest\"");
    }

    @Test
    void retrieve_reorders_results_to_submission_order_and_maps_success_and_error() {
        BatchResponse<ChatResponse> response = model().retrieve("batch_1");

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
    void retrieve_merges_output_file_and_error_file_results_in_submission_order() {
        BatchResponse<ChatResponse> response = model().retrieve("batch_witherrors");

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).isSuccess()).isTrue();
        assertThat(response.results().get(0).response().aiMessage().text()).isEqualTo("A");
        assertThat(response.results().get(1).error().message()).isEqualTo("boom");
        assertThat(response.results().get(2).error().message()).isEqualTo("rate limited");
    }

    @Test
    void cancel_calls_the_cancel_endpoint() {
        model().cancel("batch_1");
        assertThat(cancelCalled).isTrue();
    }

    @Test
    void list_maps_page_based_pagination_cursor() {
        BatchPage<ChatResponse> page = model().list(new BatchPagination(10, null));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(page.nextPageToken()).isEqualTo("1");
    }

    @Test
    void retrieve_while_running_returns_running_and_does_not_fetch_results() {
        BatchResponse<ChatResponse> response = model().retrieve("batch_running");

        assertThat(response.state()).isEqualTo(BatchState.RUNNING);
        assertThat(response.results()).isEmpty();
        assertThat(resultsFileFetched).isFalse();
    }

    @Test
    void retrieve_maps_result_line_with_error_status_code_to_failure() {
        BatchResponse<ChatResponse> response = model().retrieve("batch_statusfail");

        assertThat(response.results()).hasSize(1);
        BatchItemResult<ChatResponse> result = response.results().get(0);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error().code()).isEqualTo(400);
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
