package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenAiOfficialBatchChatModelTest {

    private static final String MODEL_NAME = "gpt-4o-mini";
    private static final String FILES_PATH = "files";
    private static final String BATCHES_PATH = "batches";
    private static final String BATCH_ID = "batch_abc";
    private static final String OUTPUT_FILE_CONTENT_PATH = "files/file-out/content";
    private static final String ERROR_FILE_CONTENT_PATH = "files/file-err/content";

    private OpenAiOfficialStubHttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = new OpenAiOfficialStubHttpClient();
    }

    private OpenAiOfficialBatchChatModel model() {
        return modelBuilder().build();
    }

    private OpenAiOfficialBatchChatModel.Builder modelBuilder() {
        return OpenAiOfficialBatchChatModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .modelName(MODEL_NAME);
    }

    private static String fileJson(String id) {
        return "{\"id\":\"" + id + "\",\"object\":\"file\",\"bytes\":123,\"created_at\":1700000000,"
                + "\"filename\":\"batch.jsonl\",\"purpose\":\"batch\",\"status\":\"processed\"}";
    }

    private static String batchJson(String status, String outputFileId, String errorFileId) {
        return "{\"id\":\"" + BATCH_ID + "\",\"object\":\"batch\",\"endpoint\":\"/v1/chat/completions\","
                + "\"input_file_id\":\"file-in\",\"completion_window\":\"24h\",\"created_at\":1700000000,"
                + "\"status\":\"" + status + "\""
                + (outputFileId == null ? "" : ",\"output_file_id\":\"" + outputFileId + "\"")
                + (errorFileId == null ? "" : ",\"error_file_id\":\"" + errorFileId + "\"")
                + "}";
    }

    private static String completionJson(String content) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1700000000,"
                + "\"model\":\"" + MODEL_NAME + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}],"
                + "\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,\"total_tokens\":11}}";
    }

    private static String successLine(int index, String content) {
        return "{\"id\":\"batch_req_" + index + "\",\"custom_id\":\"request-" + index + "\","
                + "\"response\":{\"status_code\":200,\"request_id\":\"r" + index + "\",\"body\":"
                + completionJson(content) + "},\"error\":null}";
    }

    private static BatchRequest<ChatRequest> batchOf(String... prompts) {
        List<ChatRequest> requests = java.util.Arrays.stream(prompts)
                .map(prompt ->
                        ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                .toList();
        return new BatchRequest<>(requests);
    }

    private void stubSubmit() {
        httpClient.enqueue(FILES_PATH, fileJson("file-in"));
        httpClient.enqueue(BATCHES_PATH, batchJson("validating", null, null));
    }

    @Test
    void should_upload_jsonl_with_batch_purpose_and_one_line_per_request() {
        stubSubmit();

        model().submit(batchOf("What is 1+1?", "What is 2+2?"));

        String uploadBody = httpClient.requestTo(FILES_PATH).body();
        assertThat(uploadBody).contains("name=\"purpose\"").contains("batch");
        assertThat(uploadBody).contains("filename=\"batch.jsonl\"");

        assertThat(jsonlLineOf(uploadBody, "request-0"))
                .contains("\"method\":\"POST\"")
                .contains("\"url\":\"/v1/chat/completions\"")
                .contains("What is 1+1?")
                .contains("\"model\":\"" + MODEL_NAME + "\"")
                .doesNotContain("What is 2+2?");
        assertThat(jsonlLineOf(uploadBody, "request-1"))
                .contains("What is 2+2?")
                .doesNotContain("What is 1+1?");
    }

    private static String jsonlLineOf(String uploadBody, String customId) {
        return uploadBody
                .lines()
                .filter(line -> line.startsWith("{\"custom_id\":\"" + customId + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No uploaded JSONL line for custom_id '" + customId + "' in:\n" + uploadBody));
    }

    @Test
    void should_return_batch_id_and_pending_state_on_submit() {
        stubSubmit();

        BatchResponse<ChatResponse> response = model().submit(batchOf("hi"));

        assertThat(response.batchId()).isEqualTo(BATCH_ID);
        assertThat(response.state()).isEqualTo(BatchState.PENDING);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void should_send_completion_window_batch_metadata_and_output_expiry() {
        stubSubmit();

        modelBuilder()
                .batchMetadata(Map.of("owner", "nightly-job"))
                .outputExpiresAfterSeconds(3600L)
                .build()
                .submit(batchOf("hi"));

        String createBody = httpClient.requestTo(BATCHES_PATH).body();
        assertThat(createBody)
                .contains("\"input_file_id\":\"file-in\"")
                .contains("\"endpoint\":\"/v1/chat/completions\"")
                .contains("\"completion_window\":\"24h\"")
                .contains("\"owner\":\"nightly-job\"")
                .contains("\"seconds\":3600")
                .contains("\"anchor\":\"created_at\"");
    }

    @Test
    void should_apply_per_request_parameters_over_builder_defaults() {
        stubSubmit();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .temperature(0.1)
                .build();
        modelBuilder().temperature(0.9).build().submit(new BatchRequest<>(List.of(request)));

        assertThat(httpClient.requestTo(FILES_PATH).body()).contains("\"temperature\":0.1");
    }

    @Test
    void should_apply_openai_specific_default_request_parameters() {
        stubSubmit();

        modelBuilder()
                .defaultRequestParameters(OpenAiOfficialChatRequestParameters.builder()
                        .maxCompletionTokens(7)
                        .seed(42)
                        .user("user-1")
                        .store(true)
                        .serviceTier("flex")
                        .parallelToolCalls(false)
                        .logitBias(Map.of("50256", -100))
                        .build())
                .build()
                .submit(batchOf("hi"));

        assertThat(jsonlLineOf(httpClient.requestTo(FILES_PATH).body(), "request-0"))
                .contains("\"max_completion_tokens\":7")
                .contains("\"seed\":42")
                .contains("\"user\":\"user-1\"")
                .contains("\"store\":true")
                .contains("\"service_tier\":\"flex\"")
                .contains("\"parallel_tool_calls\":false")
                .contains("\"50256\":-100");
    }

    @Test
    void should_fail_when_model_name_is_not_set() {
        OpenAiOfficialBatchChatModel model = OpenAiOfficialBatchChatModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .build();

        assertThatThrownBy(() -> model.submit(batchOf("hi")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("modelName cannot be null or blank");
    }

    @Test
    void should_return_results_in_submission_order_when_output_lines_are_out_of_order() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("completed", "file-out", null));
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(1, "second") + "\n" + successLine(0, "first"));

        BatchResponse<ChatResponse> response = model().retrieve(BATCH_ID);

        assertThat(response.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).response().aiMessage().text()).isEqualTo("first");
        assertThat(response.results().get(1).response().aiMessage().text()).isEqualTo("second");
    }

    @Test
    void should_map_response_metadata_from_the_completion() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("completed", "file-out", null));
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(0, "hello"));

        ChatResponse response = model().retrieve(BATCH_ID).results().get(0).response();

        assertThat(response.metadata().id()).isEqualTo("chatcmpl-1");
        assertThat(response.metadata().modelName()).isEqualTo(MODEL_NAME);
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().tokenUsage().totalTokenCount()).isEqualTo(11);
    }

    @Test
    void should_merge_error_file_entries_with_output_file_entries_in_submission_order() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("completed", "file-out", "file-err"));
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(0, "ok"));
        httpClient.enqueue(
                ERROR_FILE_CONTENT_PATH,
                "{\"id\":\"batch_req_1\",\"custom_id\":\"request-1\",\"response\":null,"
                        + "\"error\":{\"code\":\"batch_expired\",\"message\":\"This request expired\"}}");

        List<BatchItemResult<ChatResponse>> results = model().retrieve(BATCH_ID).results();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).error().message()).isEqualTo("This request expired");
        assertThat(results.get(1).error().details()).containsExactly(Map.of("code", "batch_expired"));
    }

    @Test
    void should_map_http_error_line_in_output_file_to_failure_with_status_code() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("completed", "file-out", null));
        httpClient.enqueue(
                OUTPUT_FILE_CONTENT_PATH,
                "{\"id\":\"batch_req_0\",\"custom_id\":\"request-0\",\"response\":{\"status_code\":400,"
                        + "\"body\":{\"error\":{\"message\":\"Invalid request\",\"type\":\"invalid_request_error\","
                        + "\"code\":\"context_length_exceeded\"}}},\"error\":null}");

        List<BatchItemResult<ChatResponse>> results = model().retrieve(BATCH_ID).results();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).error().code()).isEqualTo(400);
        assertThat(results.get(0).error().message()).isEqualTo("Invalid request");
        assertThat(results.get(0).error().details())
                .containsExactly(Map.of(
                        "type", "invalid_request_error",
                        "code", "context_length_exceeded"));
    }

    private void stubCompletedBatchWithOutput(int total, String outputContent) {
        httpClient.enqueue(
                BATCHES_PATH + "/" + BATCH_ID,
                "{\"id\":\"" + BATCH_ID + "\",\"object\":\"batch\",\"endpoint\":\"/v1/chat/completions\","
                        + "\"input_file_id\":\"file-in\",\"completion_window\":\"24h\",\"created_at\":1700000000,"
                        + "\"status\":\"completed\",\"output_file_id\":\"file-out\","
                        + "\"request_counts\":{\"total\":" + total + ",\"completed\":" + total + ",\"failed\":0}}");
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, outputContent);
    }

    @Test
    void should_fail_when_a_result_has_a_malformed_custom_id() {
        stubCompletedBatchWithOutput(
                1, successLine(0, "ok").replace("\"custom_id\":\"request-0\"", "\"custom_id\":\"request-foo\""));

        assertThatThrownBy(() -> model().retrieve(BATCH_ID))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected custom_id in batch result: request-foo");
    }

    @Test
    void should_fail_when_a_result_has_no_custom_id() {
        stubCompletedBatchWithOutput(1, successLine(0, "ok").replace("\"custom_id\":\"request-0\",", ""));

        assertThatThrownBy(() -> model().retrieve(BATCH_ID))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected custom_id in batch result: null");
    }

    @Test
    void should_fail_when_a_result_has_a_negative_custom_id() {
        stubCompletedBatchWithOutput(
                1, successLine(0, "ok").replace("\"custom_id\":\"request-0\"", "\"custom_id\":\"request--1\""));

        assertThatThrownBy(() -> model().retrieve(BATCH_ID))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected custom_id in batch result: request--1");
    }

    @Test
    void should_fail_when_a_result_is_outside_the_submitted_range() {
        stubCompletedBatchWithOutput(2, successLine(5, "stray"));

        assertThatThrownBy(() -> model().retrieve(BATCH_ID))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Batch result refers to request 5, but only 2 request(s) were submitted");
    }

    @Test
    void should_fail_when_a_custom_id_is_duplicated() {
        stubCompletedBatchWithOutput(2, successLine(0, "first") + "\n" + successLine(0, "again"));

        assertThatThrownBy(() -> model().retrieve(BATCH_ID))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate custom_id in batch result: request-0");
    }

    @Test
    void should_return_batch_level_errors_when_batch_failed() {
        httpClient.enqueue(
                BATCHES_PATH + "/" + BATCH_ID,
                "{\"id\":\"" + BATCH_ID + "\",\"object\":\"batch\",\"endpoint\":\"/v1/chat/completions\","
                        + "\"input_file_id\":\"file-in\",\"completion_window\":\"24h\",\"created_at\":1700000000,"
                        + "\"status\":\"failed\",\"errors\":{\"object\":\"list\",\"data\":[{\"code\":\"invalid_json_line\","
                        + "\"message\":\"Line 1 is not valid JSON\",\"line\":1,\"param\":null}]}}");

        BatchResponse<ChatResponse> response = model().retrieve(BATCH_ID);

        assertThat(response.state()).isEqualTo(BatchState.FAILED);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).error().message()).isEqualTo("Line 1 is not valid JSON");
        assertThat(response.results().get(0).error().details())
                .containsExactly(Map.of("code", "invalid_json_line", "line", 1L));
    }

    @Test
    void should_return_partial_results_when_batch_expired() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("expired", "file-out", "file-err"));
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(0, "done in time"));
        httpClient.enqueue(
                ERROR_FILE_CONTENT_PATH,
                "{\"custom_id\":\"request-1\",\"response\":null,"
                        + "\"error\":{\"code\":\"batch_expired\",\"message\":\"expired\"}}");

        BatchResponse<ChatResponse> response = model().retrieve(BATCH_ID);

        assertThat(response.state()).isEqualTo(BatchState.EXPIRED);
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).isSuccess()).isTrue();
        assertThat(response.results().get(1).isSuccess()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "validating,PENDING",
        "in_progress,RUNNING",
        "finalizing,RUNNING",
        "cancelling,RUNNING",
        "completed,SUCCEEDED",
        "failed,FAILED",
        "cancelled,CANCELLED",
        "expired,EXPIRED",
        "some_future_status,UNSPECIFIED"
    })
    void should_map_every_openai_status_to_a_batch_state(String openAiStatus, BatchState expectedState) {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson(openAiStatus, null, null));

        assertThat(model().retrieve(BATCH_ID).state()).isEqualTo(expectedState);
    }

    @Test
    void should_cancel_batch() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID + "/cancel", batchJson("cancelling", null, null));

        model().cancel(BATCH_ID);

        assertThat(httpClient
                        .requestTo(BATCHES_PATH + "/" + BATCH_ID + "/cancel")
                        .method())
                .isEqualTo("POST");
    }

    @Test
    void should_list_batches_and_return_last_id_as_next_page_token_when_more_are_available() {
        httpClient.enqueue(
                BATCHES_PATH,
                "{\"object\":\"list\",\"data\":[" + batchJson("completed", "file-out", null) + "],\"has_more\":true}");

        BatchPage<ChatResponse> page = model().list(new BatchPagination(10, "batch_previous"));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).batchId()).isEqualTo(BATCH_ID);
        assertThat(page.nextPageToken()).isEqualTo(BATCH_ID);
        assertThat(httpClient.requestTo(BATCHES_PATH).path()).isEqualTo(BATCHES_PATH);
    }

    @Test
    void should_return_null_next_page_token_when_no_more_batches_are_available() {
        httpClient.enqueue(
                BATCHES_PATH,
                "{\"object\":\"list\",\"data\":[" + batchJson("completed", "file-out", null) + "],\"has_more\":false}");

        assertThat(model().list(null).nextPageToken()).isNull();
    }

    @Test
    void should_fail_when_requests_use_different_models() {
        ChatRequest other = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .modelName("gpt-4o")
                .build();
        ChatRequest defaulted =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();

        assertThatThrownBy(() -> model().submit(new BatchRequest<>(List.of(defaulted, other))))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Batch requests cannot use different models; " + "all requests must use the same model: ["
                        + MODEL_NAME + ", gpt-4o]");
    }

    @Test
    void should_use_microsoft_foundry_endpoint_and_deployment_name_as_model() {
        stubSubmit();

        OpenAiOfficialBatchChatModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .isMicrosoftFoundry(true)
                .modelName(MODEL_NAME)
                .microsoftFoundryDeploymentName("my-deployment")
                .build()
                .submit(batchOf("hi"));

        assertThat(httpClient.requestTo(BATCHES_PATH).body())
                .contains("\"endpoint\":\"/chat/completions\"")
                .doesNotContain("/v1/chat/completions");
        assertThat(jsonlLineOf(httpClient.requestTo(FILES_PATH).body(), "request-0"))
                .contains("\"model\":\"my-deployment\"")
                .contains("\"url\":\"/v1/chat/completions\"");
    }

    @Test
    void should_fail_when_used_with_github_models() {
        assertThatThrownBy(() -> OpenAiOfficialBatchChatModel.builder()
                        .apiKey("test-key")
                        .isGitHubModels(true)
                        .modelName(MODEL_NAME)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The Batch API is not supported by GitHub Models");
    }

    @Test
    void should_place_sparse_results_at_their_own_request_index() {
        httpClient.enqueue(
                BATCHES_PATH + "/" + BATCH_ID,
                "{\"id\":\"" + BATCH_ID + "\",\"object\":\"batch\",\"endpoint\":\"/v1/chat/completions\","
                        + "\"input_file_id\":\"file-in\",\"completion_window\":\"24h\",\"created_at\":1700000000,"
                        + "\"status\":\"expired\",\"output_file_id\":\"file-out\","
                        + "\"request_counts\":{\"total\":3,\"completed\":2,\"failed\":0}}");
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(2, "third") + "\n" + successLine(0, "first"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(BATCH_ID).results();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).response().aiMessage().text()).isEqualTo("first");
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).error().message()).isEqualTo("No result was returned for this request");
        assertThat(results.get(2).response().aiMessage().text()).isEqualTo("third");
    }

    @Test
    void should_reject_topK_which_openai_does_not_support() {
        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("hi")).topK(5).build();

        assertThatThrownBy(() -> model().submit(new BatchRequest<>(List.of(request))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("'topK' parameter is not supported by OpenAI");
    }

    @Test
    void should_reject_per_request_model_override_on_microsoft_foundry() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .modelName("another-deployment")
                .build();

        OpenAiOfficialBatchChatModel model = OpenAiOfficialBatchChatModel.builder()
                .openAIClient(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(httpClient)
                        .build()))
                .isMicrosoftFoundry(true)
                .modelName(MODEL_NAME)
                .microsoftFoundryDeploymentName("my-deployment")
                .build();

        assertThatThrownBy(() -> model.submit(new BatchRequest<>(List.of(request))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Microsoft Foundry batch requests do not support overriding modelName per request; "
                        + "configure the batch deployment when building the model");
    }

    @Test
    void should_fail_when_github_models_is_detected_from_the_base_url() {
        assertThatThrownBy(() -> OpenAiOfficialBatchChatModel.builder()
                        .apiKey("test-key")
                        .baseUrl("https://models.github.ai/inference")
                        .modelName(MODEL_NAME)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The Batch API is not supported by GitHub Models");
    }

    @Test
    void should_send_input_file_expiry_when_configured() {
        stubSubmit();

        modelBuilder().inputFileExpiresAfterSeconds(1209600L).build().submit(batchOf("hi"));

        assertThat(httpClient.requestTo(FILES_PATH).body())
                .contains("name=\"expires_after[anchor]\"")
                .contains("created_at")
                .contains("name=\"expires_after[seconds]\"")
                .contains("1209600");
    }

    @Test
    void should_serialize_tools_and_map_returned_tool_calls() {
        stubSubmit();
        ToolSpecification tool = ToolSpecification.builder()
                .name("get_weather")
                .description("Get the weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        model().submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("weather in Paris?"))
                .toolSpecifications(tool)
                .build())));

        assertThat(jsonlLineOf(httpClient.requestTo(FILES_PATH).body(), "request-0"))
                .contains("\"tools\":[")
                .contains("\"name\":\"get_weather\"")
                .contains("\"city\"");

        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("completed", "file-out", null));
        httpClient.enqueue(
                OUTPUT_FILE_CONTENT_PATH,
                "{\"custom_id\":\"request-0\",\"response\":{\"status_code\":200,\"body\":"
                        + "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1700000000,"
                        + "\"model\":\"" + MODEL_NAME + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                        + "\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\","
                        + "\"type\":\"function\",\"function\":{\"name\":\"get_weather\","
                        + "\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}]}}]}},\"error\":null}");

        ChatResponse response = model().retrieve(BATCH_ID).results().get(0).response();
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("get_weather");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void should_serialize_json_schema_response_format() {
        stubSubmit();

        model().submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("extract"))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .required("name")
                                        .build())
                                .build())
                        .build())
                .build())));

        assertThat(jsonlLineOf(httpClient.requestTo(FILES_PATH).body(), "request-0"))
                .contains("\"response_format\"")
                .contains("json_schema")
                .contains("\"name\":\"Person\"");
    }

    @Test
    void should_serialize_image_content() {
        stubSubmit();

        model().submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from(
                        TextContent.from("what is this?"), ImageContent.from("https://example.com/cat.png")))
                .build())));

        assertThat(jsonlLineOf(httpClient.requestTo(FILES_PATH).body(), "request-0"))
                .contains("image_url")
                .contains("https://example.com/cat.png");
    }

    @Test
    void should_send_limit_and_after_on_the_wire_when_listing() {
        httpClient.enqueue(BATCHES_PATH, "{\"object\":\"list\",\"data\":[],\"has_more\":false}");

        model().list(new BatchPagination(25, "batch_previous"));

        assertThat(httpClient.requestTo(BATCHES_PATH).query())
                .contains("limit=25")
                .contains("after=batch_previous");
    }

    @Test
    void should_return_partial_results_when_batch_was_cancelled() {
        httpClient.enqueue(BATCHES_PATH + "/" + BATCH_ID, batchJson("cancelled", "file-out", null));
        httpClient.enqueue(OUTPUT_FILE_CONTENT_PATH, successLine(0, "finished before cancel"));

        BatchResponse<ChatResponse> response = model().retrieve(BATCH_ID);

        assertThat(response.state()).isEqualTo(BatchState.CANCELLED);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).response().aiMessage().text()).isEqualTo("finished before cancel");
    }

    @Test
    void should_fail_when_batch_is_empty() {
        assertThatThrownBy(() -> model().submit(new BatchRequest<>(List.of())))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("requests cannot be null or empty");
    }
}
