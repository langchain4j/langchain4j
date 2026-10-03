package dev.langchain4j.model.bedrock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobRequest;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.GetModelInvocationJobRequest;
import software.amazon.awssdk.services.bedrock.model.GetModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.ListModelInvocationJobsRequest;
import software.amazon.awssdk.services.bedrock.model.ListModelInvocationJobsResponse;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobStatus;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobSummary;
import software.amazon.awssdk.services.bedrock.model.S3InputFormat;
import software.amazon.awssdk.services.bedrock.model.StopModelInvocationJobRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.utils.SdkAutoCloseable;

class BedrockBatchChatModelTest {

    private static final String MODEL_ID = "model-x";
    private static final String JOB_ARN = "arn:aws:bedrock:us-east-1:123456789012:model-invocation-job/job-123";
    private static final String OUTPUT_BUCKET = "out-bucket";
    private static final String OUTPUT_PREFIX = "out/job-123/";

    private final BedrockClient bedrock = mock(BedrockClient.class);
    private final S3Client s3 = mock(S3Client.class);

    private BedrockBatchChatModel.Builder modelBuilder() {
        return BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .modelId(MODEL_ID)
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .inputS3Uri("s3://in-bucket/in");
    }

    private BedrockBatchChatModel model() {
        return modelBuilder().build();
    }

    private static ChatRequest request(String text) {
        return ChatRequest.builder().messages(UserMessage.from(text)).build();
    }

    private static ChatRequest request(String text, ChatRequestParameters parameters) {
        return ChatRequest.builder()
                .messages(UserMessage.from(text))
                .parameters(parameters)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void stubSubmit() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(bedrock.createModelInvocationJob(any(Consumer.class)))
                .thenReturn(CreateModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .build());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> uploadedRecords() throws Exception {
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(Consumer.class), body.capture());
        String jsonl =
                new String(body.getValue().contentStreamProvider().newStream().readAllBytes(), UTF_8);
        return jsonl.lines().map(BedrockBatchConverseMapper::fromJsonLine).toList();
    }

    @SuppressWarnings("unchecked")
    private CreateModelInvocationJobRequest createdJob() {
        ArgumentCaptor<Consumer<CreateModelInvocationJobRequest.Builder>> job = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).createModelInvocationJob(job.capture());
        CreateModelInvocationJobRequest.Builder builder = CreateModelInvocationJobRequest.builder();
        job.getValue().accept(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> modelInput(Map<String, Object> record) {
        return (Map<String, Object>) record.get("modelInput");
    }

    @SuppressWarnings("unchecked")
    private static String userText(Map<String, Object> record) {
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) modelInput(record).get("messages");
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) messages.get(0).get("content");
        return (String) content.get(0).get("text");
    }

    @SuppressWarnings("unchecked")
    private void stubJob(ModelInvocationJobStatus status, String message, Map<String, String> outputFiles) {
        when(bedrock.getModelInvocationJob(any(Consumer.class)))
                .thenReturn(GetModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .status(status)
                        .message(message)
                        .build());
        when(s3.listObjectsV2(any(Consumer.class))).thenAnswer(invocation -> {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder();
            ((Consumer<ListObjectsV2Request.Builder>) invocation.getArgument(0)).accept(request);
            ListObjectsV2Request listing = request.build();
            if (!listing.bucket().equals(OUTPUT_BUCKET) || !listing.prefix().equals(OUTPUT_PREFIX)) {
                return ListObjectsV2Response.builder().build();
            }
            return ListObjectsV2Response.builder()
                    .contents(outputFiles.keySet().stream()
                            .map(key -> S3Object.builder().key(key).build())
                            .toList())
                    .build();
        });
        when(s3.getObjectAsBytes(any(Consumer.class))).thenAnswer(invocation -> {
            GetObjectRequest.Builder request = GetObjectRequest.builder();
            ((Consumer<GetObjectRequest.Builder>) invocation.getArgument(0)).accept(request);
            assertThat(request.build().bucket()).isEqualTo(OUTPUT_BUCKET);
            String content = outputFiles.get(request.build().key());
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content.getBytes(UTF_8));
        });
    }

    private void stubJob(ModelInvocationJobStatus status, Map<String, String> outputFiles) {
        stubJob(status, null, outputFiles);
    }

    private static Map<String, String> files(String... keysAndContents) {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < keysAndContents.length; i += 2) {
            files.put(keysAndContents[i], keysAndContents[i + 1]);
        }
        return files;
    }

    private static String success(String recordId, String text) {
        return "{\"recordId\":\"" + recordId + "\",\"modelOutput\":{\"output\":{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"text\":\"" + text + "\"}]}},\"stopReason\":\"end_turn\","
                + "\"usage\":{\"inputTokens\":3,\"outputTokens\":2}}}";
    }

    private static String failure(String recordId, int code, String message) {
        return "{\"recordId\":\"" + recordId + "\",\"error\":{\"errorCode\":" + code + ",\"errorMessage\":\"" + message
                + "\"}}";
    }

    private static String manifest(int totalRecordCount) {
        return "{\"totalRecordCount\":" + totalRecordCount + ",\"processedRecordCount\":" + totalRecordCount + "}";
    }

    private static String text(BatchItemResult<ChatResponse> result) {
        return result.response().aiMessage().text();
    }

    @Test
    void should_upload_one_single_line_record_per_request_and_create_the_job() throws Exception {
        stubSubmit();

        BatchResponse<ChatResponse> response = model().submit(new BatchRequest<>(List.of(request("A"), request("B"))));

        assertThat(response.batchId()).isEqualTo(JOB_ARN);
        assertThat(response.state()).isEqualTo(BatchState.PENDING);
        assertThat(response.results()).isEmpty();

        List<Map<String, Object>> records = uploadedRecords();
        assertThat(records).hasSize(2);
        assertThat(records).extracting(record -> record.get("recordId")).containsExactly("r0000000000", "r0000000001");
        assertThat(records).extracting(BedrockBatchChatModelTest::userText).containsExactly("A", "B");

        CreateModelInvocationJobRequest job = createdJob();
        assertThat(job.jobName()).startsWith("lc4j-batch-");
        assertThat(job.roleArn()).isEqualTo("arn:role");
        assertThat(job.modelId()).isEqualTo(MODEL_ID);
        assertThat(job.inputDataConfig().s3InputDataConfig().s3Uri())
                .startsWith("s3://in-bucket/in/lc4j-batch-")
                .endsWith("/input.jsonl");
        assertThat(job.inputDataConfig().s3InputDataConfig().s3InputFormat()).isEqualTo(S3InputFormat.JSONL);
        assertThat(job.outputDataConfig().s3OutputDataConfig().s3Uri()).isEqualTo("s3://out-bucket/out");
        assertThat(job.timeoutDurationInHours()).isNull();
    }

    @Test
    void should_generate_ascii_record_ids_whatever_the_default_locale() throws Exception {
        stubSubmit();
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            model().submit(new BatchRequest<>(List.of(request("A"), request("B"))));
        } finally {
            Locale.setDefault(defaultLocale);
        }

        assertThat(uploadedRecords())
                .extracting(record -> record.get("recordId"))
                .containsExactly("r0000000000", "r0000000001");
    }

    @Test
    void should_let_a_request_override_the_default_request_parameters() throws Exception {
        stubSubmit();

        modelBuilder()
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .temperature(0.2)
                        .maxOutputTokens(100)
                        .build())
                .build()
                .submit(new BatchRequest<>(List.of(
                        request("A"),
                        request(
                                "B",
                                ChatRequestParameters.builder().temperature(0.9).build()))));

        List<Map<String, Object>> records = uploadedRecords();
        assertThat(modelInput(records.get(0)).get("inferenceConfig"))
                .isEqualTo(Map.of("maxTokens", 100, "temperature", 0.2));
        assertThat(modelInput(records.get(1)).get("inferenceConfig"))
                .isEqualTo(Map.of("maxTokens", 100, "temperature", 0.9));
    }

    @Test
    void should_merge_additional_model_request_fields_key_by_key() throws Exception {
        stubSubmit();

        modelBuilder()
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .additionalModelRequestFields(Map.of("top_k", 10, "budget", 1000))
                        .build())
                .build()
                .submit(new BatchRequest<>(List.of(request(
                        "A",
                        BedrockChatRequestParameters.builder()
                                .additionalModelRequestFields(Map.of("budget", 2000))
                                .build()))));

        assertThat(modelInput(uploadedRecords().get(0)).get("additionalModelRequestFields"))
                .isEqualTo(Map.of("top_k", 10, "budget", 2000));
    }

    @Test
    void should_send_the_guardrail_configuration_and_request_metadata() throws Exception {
        stubSubmit();

        model().submit(new BatchRequest<>(List.of(request(
                "A",
                BedrockChatRequestParameters.builder()
                        .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                                .guardrailIdentifier("guardrail-1")
                                .guardrailVersion("2")
                                .build())
                        .requestMetadata(Map.of("tenant", "acme"))
                        .build()))));

        Map<String, Object> modelInput = modelInput(uploadedRecords().get(0));
        assertThat(modelInput.get("guardrailConfig"))
                .isEqualTo(Map.of("guardrailIdentifier", "guardrail-1", "guardrailVersion", "2"));
        assertThat(modelInput.get("requestMetadata")).isEqualTo(Map.of("tenant", "acme"));
    }

    @Test
    void should_send_the_job_timeout_in_whole_hours_rounding_up() {
        stubSubmit();

        modelBuilder()
                .jobTimeout(Duration.ofMinutes(24 * 60 + 30))
                .build()
                .submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(createdJob().timeoutDurationInHours()).isEqualTo(25);
    }

    @Test
    void should_send_a_whole_hour_job_timeout_unchanged() {
        stubSubmit();

        modelBuilder().jobTimeout(Duration.ofHours(48)).build().submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(createdJob().timeoutDurationInHours()).isEqualTo(48);
    }

    @Test
    void should_reject_tool_specifications_without_uploading_anything() {
        ChatRequest withTools = ChatRequest.builder()
                .messages(UserMessage.from("A"))
                .toolSpecifications(ToolSpecification.builder().name("tool").build())
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(withTools))))
                .withMessage("Tool calling is not supported by Bedrock batch inference");
        verify(s3, never()).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_reject_a_parameter_bedrock_chat_model_rejects_without_uploading_anything() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(
                        request("A"),
                        request("B", ChatRequestParameters.builder().topK(5).build())))))
                .withMessage("'topK' parameter is not supported yet by this model provider");
        verify(s3, never()).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void should_reject_a_default_parameter_bedrock_chat_model_rejects() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelBuilder()
                        .defaultRequestParameters(ChatRequestParameters.builder()
                                .frequencyPenalty(0.5)
                                .build())
                        .build())
                .withMessage("'frequencyPenalty' parameter is not supported yet by this model provider");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_reject_prompt_caching_without_uploading_anything() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(request(
                        "A",
                        BedrockChatRequestParameters.builder()
                                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                                .build())))))
                .withMessage("Prompt caching is not supported by Bedrock batch inference");
        verify(s3, never()).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    void should_reject_prompt_caching_set_on_the_default_request_parameters() {
        BedrockBatchChatModel model = modelBuilder()
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .promptCaching(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE)
                        .build())
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.submit(new BatchRequest<>(List.of(request("A")))))
                .withMessage("Prompt caching is not supported by Bedrock batch inference");
    }

    @Test
    void should_reject_a_service_tier() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(request(
                        "A",
                        BedrockChatRequestParameters.builder()
                                .serviceTier(BedrockServiceTier.FLEX)
                                .build())))))
                .withMessage("serviceTier is not supported by BedrockBatchChatModel");
    }

    @Test
    void should_reject_a_json_response_format() {
        ChatRequest structured = ChatRequest.builder()
                .messages(UserMessage.from("A"))
                .responseFormat(ResponseFormat.builder()
                        .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("answer")
                                .rootElement(JsonObjectSchema.builder().build())
                                .build())
                        .build())
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(structured))))
                .withMessage("Structured output is not supported by Bedrock batch inference");
    }

    @Test
    void should_accept_a_text_response_format() throws Exception {
        stubSubmit();

        model().submit(new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("A"))
                .responseFormat(ResponseFormat.TEXT)
                .build())));

        assertThat(uploadedRecords()).hasSize(1);
    }

    @Test
    void should_reject_a_request_for_another_model() {
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(new BatchRequest<>(List.of(request(
                        "A",
                        ChatRequestParameters.builder().modelName("model-y").build())))))
                .withMessage("A Bedrock batch job runs every request against one model, 'model-x', "
                        + "so a request cannot use 'model-y'");
    }

    @Test
    void should_accept_a_request_that_names_the_job_model() throws Exception {
        stubSubmit();

        model().submit(new BatchRequest<>(List.of(
                request("A", ChatRequestParameters.builder().modelName(MODEL_ID).build()))));

        assertThat(uploadedRecords()).hasSize(1);
    }

    @Test
    void should_default_the_model_id_to_the_default_request_parameters_model_name() {
        stubSubmit();

        BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .defaultRequestParameters(
                        ChatRequestParameters.builder().modelName("model-z").build())
                .build()
                .submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(createdJob().modelId()).isEqualTo("model-z");
    }

    @Test
    void should_prefer_the_builder_model_id_over_the_default_request_parameters_model_name() throws Exception {
        stubSubmit();

        modelBuilder()
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .modelName("model-z")
                        .temperature(0.3)
                        .build())
                .build()
                .submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(createdJob().modelId()).isEqualTo(MODEL_ID);
        assertThat(modelInput(uploadedRecords().get(0)).get("inferenceConfig")).isEqualTo(Map.of("temperature", 0.3));
    }

    @Test
    void should_keep_the_bedrock_default_parameters_when_the_model_id_is_set_on_the_builder() throws Exception {
        stubSubmit();

        modelBuilder()
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .modelName("model-z")
                        .additionalModelRequestFields(Map.of("top_k", 10))
                        .build())
                .build()
                .submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(modelInput(uploadedRecords().get(0)).get("additionalModelRequestFields"))
                .isEqualTo(Map.of("top_k", 10));
    }

    @Test
    void should_upload_the_input_under_the_output_location_when_no_input_location_is_set() {
        stubSubmit();

        BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .modelId(MODEL_ID)
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .build()
                .submit(new BatchRequest<>(List.of(request("A"))));

        assertThat(createdJob().inputDataConfig().s3InputDataConfig().s3Uri())
                .startsWith("s3://out-bucket/out/lc4j-batch-");
    }

    @Test
    void should_require_a_model_id_a_role_and_an_output_location() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BedrockBatchChatModel.builder()
                        .bedrockClient(bedrock)
                        .s3Client(s3)
                        .roleArn("arn:role")
                        .outputS3Uri("s3://out-bucket/out")
                        .build())
                .withMessageContaining("modelId");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BedrockBatchChatModel.builder()
                        .bedrockClient(bedrock)
                        .s3Client(s3)
                        .modelId(MODEL_ID)
                        .outputS3Uri("s3://out-bucket/out")
                        .build())
                .withMessageContaining("roleArn");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BedrockBatchChatModel.builder()
                        .bedrockClient(bedrock)
                        .s3Client(s3)
                        .modelId(MODEL_ID)
                        .roleArn("arn:role")
                        .build())
                .withMessageContaining("outputS3Uri");
    }

    @Test
    void should_reject_an_output_location_that_is_not_an_s3_uri() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> modelBuilder().outputS3Uri("https://bucket/out").build())
                .withMessage("Not an S3 URI (expected s3://bucket/key): https://bucket/out");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_no_results_while_the_job_is_running() {
        stubJob(ModelInvocationJobStatus.IN_PROGRESS, Map.of());

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.RUNNING);
        assertThat(response.results()).isEmpty();
        verify(s3, never()).listObjectsV2(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_retrieve_the_job_by_its_arn() {
        stubJob(ModelInvocationJobStatus.IN_PROGRESS, Map.of());

        model().retrieve(JOB_ARN);

        ArgumentCaptor<Consumer<GetModelInvocationJobRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).getModelInvocationJob(captor.capture());
        GetModelInvocationJobRequest.Builder builder = GetModelInvocationJobRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().jobIdentifier()).isEqualTo(JOB_ARN);
    }

    @Test
    void should_return_results_in_request_order() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000001", "second") + "\n" + success("r0000000000", "first") + "\n"));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(response.results()).hasSize(2);
        assertThat(text(response.results().get(0))).isEqualTo("first");
        assertThat(text(response.results().get(1))).isEqualTo("second");
        assertThat(response.results().get(0).response().metadata().modelName()).isEqualTo(MODEL_ID);
        assertThat(response.results().get(0).response().tokenUsage().inputTokenCount())
                .isEqualTo(3);
    }

    @Test
    void should_map_a_failed_record_to_its_error_code_and_message() {
        stubJob(
                ModelInvocationJobStatus.PARTIALLY_COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "first") + "\n" + failure("r0000000001", 400, "bad request") + "\n"));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.SUCCEEDED);
        BatchItemResult<ChatResponse> failed = response.results().get(1);
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.error().code()).isEqualTo(400);
        assertThat(failed.error().message()).isEqualTo("bad request");
    }

    @Test
    void should_fill_a_request_without_a_result_with_a_failure() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(3),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "first") + "\n" + success("r0000000002", "third") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).hasSize(3);
        assertThat(text(results.get(0))).isEqualTo("first");
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).error().code()).isZero();
        assertThat(results.get(1).error().message()).isEqualTo("No result was returned for this request");
        assertThat(text(results.get(2))).isEqualTo("third");
    }

    @Test
    void should_return_the_partial_results_of_a_stopped_job() {
        stubJob(
                ModelInvocationJobStatus.STOPPED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "first") + "\n"));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.CANCELLED);
        assertThat(response.results()).hasSize(2);
        assertThat(text(response.results().get(0))).isEqualTo("first");
        assertThat(response.results().get(1).isSuccess()).isFalse();
    }

    @Test
    void should_return_the_partial_results_of_an_expired_job() {
        stubJob(
                ModelInvocationJobStatus.EXPIRED,
                files(OUTPUT_PREFIX + "input.jsonl.out", success("r0000000000", "first") + "\n"));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.EXPIRED);
        assertThat(response.results()).hasSize(1);
        assertThat(text(response.results().get(0))).isEqualTo("first");
    }

    @ParameterizedTest
    @CsvSource({
        "r+000000001, r+000000000",
        "r000000001, r000000000",
        "r00000000001, r00000000000",
        "R0000000001, R0000000000",
        "2, 1",
        "CALL0000002, CALL0000001"
    })
    void should_keep_every_result_in_output_order_when_record_ids_are_not_its_own(String firstId, String secondId) {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success(firstId, "listed first") + "\n" + success(secondId, "listed second") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results)
                .extracting(BedrockBatchChatModelTest::text)
                .containsExactly("listed first", "listed second");
    }

    @Test
    void should_keep_every_result_in_output_order_when_a_record_id_is_duplicated() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "one") + "\n" + success("r0000000000", "two") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).extracting(BedrockBatchChatModelTest::text).containsExactly("one", "two");
    }

    @Test
    void should_keep_every_result_in_output_order_when_a_record_id_is_out_of_range() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(1),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "one") + "\n" + success("r0000000005", "two") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).extracting(BedrockBatchChatModelTest::text).containsExactly("one", "two");
    }

    @Test
    void should_keep_an_unreadable_result_line_as_a_failure() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(2),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000001", "second") + "\n" + "{not json\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).hasSize(2);
        assertThat(text(results.get(0))).isEqualTo("second");
        assertThat(results.get(1).isSuccess()).isFalse();
        assertThat(results.get(1).error().message()).isEqualTo("The result line could not be parsed");
    }

    @Test
    void should_still_correlate_when_the_manifest_is_unreadable() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        "{not json",
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000001", "second") + "\n" + success("r0000000000", "first") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).extracting(BedrockBatchChatModelTest::text).containsExactly("first", "second");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_propagate_a_failure_to_read_the_manifest() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(1),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "first") + "\n"));
        when(s3.getObjectAsBytes(any(Consumer.class))).thenAnswer(invocation -> {
            GetObjectRequest.Builder getObject = GetObjectRequest.builder();
            ((Consumer<GetObjectRequest.Builder>) invocation.getArgument(0)).accept(getObject);
            if (getObject.build().key().endsWith("manifest.json.out")) {
                throw S3Exception.builder().message("Access Denied").build();
            }
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().build(), (success("r0000000000", "first") + "\n").getBytes(UTF_8));
        });

        assertThatExceptionOfType(S3Exception.class)
                .isThrownBy(() -> model().retrieve(JOB_ARN))
                .withMessage("Access Denied");
    }

    @Test
    void should_read_every_result_file() {
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out", manifest(2),
                        OUTPUT_PREFIX + "input-1.jsonl.out", success("r0000000001", "second") + "\n",
                        OUTPUT_PREFIX + "input-2.jsonl.out", success("r0000000000", "first") + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results).extracting(BedrockBatchChatModelTest::text).containsExactly("first", "second");
    }

    @Test
    void should_report_a_failed_job_without_output_as_a_single_failure() {
        stubJob(ModelInvocationJobStatus.FAILED, "The role cannot read the input", Map.of());

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.FAILED);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).error().code()).isZero();
        assertThat(response.results().get(0).error().message()).isEqualTo("The role cannot read the input");
    }

    @Test
    void should_return_no_results_for_a_completed_job_without_output() {
        stubJob(ModelInvocationJobStatus.COMPLETED, Map.of());

        assertThat(model().retrieve(JOB_ARN).results()).isEmpty();
    }

    @Test
    void should_return_thinking_only_when_enabled() {
        String withReasoning = "{\"recordId\":\"r0000000000\",\"modelOutput\":{\"output\":{\"message\":{\"content\":["
                + "{\"reasoningContent\":{\"reasoningText\":{\"text\":\"hmm\",\"signature\":\"sig\"}}},"
                + "{\"text\":\"answer\"}]}},\"stopReason\":\"end_turn\"}}";
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(1),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        withReasoning));

        assertThat(model().retrieve(JOB_ARN)
                        .results()
                        .get(0)
                        .response()
                        .aiMessage()
                        .thinking())
                .isNull();
        assertThat(modelBuilder()
                        .returnThinking(true)
                        .build()
                        .retrieve(JOB_ARN)
                        .results()
                        .get(0)
                        .response()
                        .aiMessage()
                        .thinking())
                .isEqualTo("hmm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_stop_the_job_on_cancel() {
        model().cancel(JOB_ARN);

        ArgumentCaptor<Consumer<StopModelInvocationJobRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).stopModelInvocationJob(captor.capture());
        StopModelInvocationJobRequest.Builder builder = StopModelInvocationJobRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().jobIdentifier()).isEqualTo(JOB_ARN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_list_jobs_and_pass_the_page_size_and_token() {
        when(bedrock.listModelInvocationJobs(any(Consumer.class)))
                .thenReturn(ListModelInvocationJobsResponse.builder()
                        .invocationJobSummaries(ModelInvocationJobSummary.builder()
                                .jobArn(JOB_ARN)
                                .status(ModelInvocationJobStatus.IN_PROGRESS)
                                .build())
                        .nextToken("next")
                        .build());

        BatchPage<ChatResponse> page = model().list(new BatchPagination(5, "token"));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).batchId()).isEqualTo(JOB_ARN);
        assertThat(page.batches().get(0).state()).isEqualTo(BatchState.RUNNING);
        assertThat(page.nextPageToken()).isEqualTo("next");

        ArgumentCaptor<Consumer<ListModelInvocationJobsRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).listModelInvocationJobs(captor.capture());
        ListModelInvocationJobsRequest.Builder builder = ListModelInvocationJobsRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().maxResults()).isEqualTo(5);
        assertThat(builder.build().nextToken()).isEqualTo("token");
    }

    @Test
    void should_return_every_result_at_the_position_of_its_request_whatever_the_output_order() throws Exception {
        stubSubmit();
        List<ChatRequest> requests =
                IntStream.range(0, 12).mapToObj(i -> request("question " + i)).toList();

        model().submit(new BatchRequest<>(requests));

        List<String> resultLines = new ArrayList<>();
        for (Map<String, Object> record : uploadedRecords()) {
            resultLines.add(success((String) record.get("recordId"), "answer to " + userText(record)));
        }
        List<String> shuffled = new ArrayList<>(resultLines);
        Collections.shuffle(shuffled, new Random(42));
        assertThat(shuffled).isNotEqualTo(resultLines);
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(12),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        String.join("\n", shuffled) + "\n"));

        List<BatchItemResult<ChatResponse>> results = model().retrieve(JOB_ARN).results();

        assertThat(results)
                .extracting(BedrockBatchChatModelTest::text)
                .containsExactlyElementsOf(IntStream.range(0, 12)
                        .mapToObj(i -> "answer to question " + i)
                        .toList());
    }

    @Test
    void should_drop_empty_segments_from_the_s3_locations() {
        stubSubmit();
        stubJob(
                ModelInvocationJobStatus.COMPLETED,
                files(
                        OUTPUT_PREFIX + "manifest.json.out",
                        manifest(1),
                        OUTPUT_PREFIX + "input.jsonl.out",
                        success("r0000000000", "first") + "\n"));
        BedrockBatchChatModel model = modelBuilder()
                .outputS3Uri("s3://out-bucket//out//")
                .inputS3Uri("s3://in-bucket/in//")
                .build();

        model.submit(new BatchRequest<>(List.of(request("A"))));

        CreateModelInvocationJobRequest job = createdJob();
        assertThat(job.outputDataConfig().s3OutputDataConfig().s3Uri()).isEqualTo("s3://out-bucket/out");
        assertThat(job.inputDataConfig().s3InputDataConfig().s3Uri())
                .matches("s3://in-bucket/in/lc4j-batch-[0-9a-f-]+/input\\.jsonl");
        assertThat(text(model.retrieve(JOB_ARN).results().get(0))).isEqualTo("first");
    }

    @Test
    void should_not_close_clients_passed_to_the_builder() {
        model().close();

        verify(bedrock, never()).close();
        verify(s3, never()).close();
    }

    @Test
    void should_not_close_a_credentials_provider_passed_to_the_builder() {
        CloseTrackingCredentialsProvider credentialsProvider = new CloseTrackingCredentialsProvider();

        BedrockBatchChatModel.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentialsProvider)
                .modelId(MODEL_ID)
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .build()
                .close();

        assertThat(credentialsProvider.closed).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "SUBMITTED, PENDING",
        "VALIDATING, PENDING",
        "SCHEDULED, PENDING",
        "IN_PROGRESS, RUNNING",
        "STOPPING, RUNNING",
        "COMPLETED, SUCCEEDED",
        "PARTIALLY_COMPLETED, SUCCEEDED",
        "FAILED, FAILED",
        "STOPPED, CANCELLED",
        "EXPIRED, EXPIRED",
        "UNKNOWN_TO_SDK_VERSION, UNSPECIFIED"
    })
    void should_map_every_job_status(ModelInvocationJobStatus status, BatchState expected) {
        assertThat(BedrockBatchChatModel.toBatchState(status)).isEqualTo(expected);
    }

    @Test
    void should_map_a_missing_job_status_to_unspecified() {
        assertThat(BedrockBatchChatModel.toBatchState(null)).isEqualTo(BatchState.UNSPECIFIED);
    }

    private static class CloseTrackingCredentialsProvider implements AwsCredentialsProvider, SdkAutoCloseable {

        private boolean closed;

        @Override
        public AwsCredentials resolveCredentials() {
            return AwsBasicCredentials.create("access-key", "secret-key");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
