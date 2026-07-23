package dev.langchain4j.model.bedrock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
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
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobRequest;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.GetModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.ListModelInvocationJobsResponse;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobStatus;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationJobSummary;
import software.amazon.awssdk.services.bedrock.model.S3InputFormat;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

class BedrockBatchChatModelTest {

    private static final String JOB_ARN = "arn:aws:bedrock:us-east-1:123456789012:model-invocation-job/job-123";

    private final BedrockClient bedrock = org.mockito.Mockito.mock(BedrockClient.class);
    private final S3Client s3 = org.mockito.Mockito.mock(S3Client.class);

    private BedrockBatchChatModel model() {
        return BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .modelId("model-x")
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .inputS3Uri("s3://in-bucket/in")
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submit_uploads_jsonl_and_creates_job() throws Exception {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(bedrock.createModelInvocationJob(any(Consumer.class)))
                .thenReturn(CreateModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .build());

        BatchResponse<ChatResponse> response = model().submit(new BatchRequest<>(List.of(
                ChatRequest.builder().messages(UserMessage.from("A")).build(),
                ChatRequest.builder().messages(UserMessage.from("B")).build())));

        assertThat(response.batchId()).isEqualTo(JOB_ARN);
        assertThat(response.state()).isEqualTo(BatchState.PENDING);

        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(Consumer.class), body.capture());
        String jsonl =
                new String(body.getValue().contentStreamProvider().newStream().readAllBytes(), UTF_8);
        assertThat(jsonl.strip().split("\n")).hasSize(2);
        assertThat(jsonl).contains("\"recordId\":\"r0000000000\"").contains("\"recordId\":\"r0000000001\"");
        assertThat(jsonl).doesNotContain("request-");

        ArgumentCaptor<Consumer<CreateModelInvocationJobRequest.Builder>> job = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).createModelInvocationJob(job.capture());
        CreateModelInvocationJobRequest.Builder builder = CreateModelInvocationJobRequest.builder();
        job.getValue().accept(builder);
        CreateModelInvocationJobRequest built = builder.build();
        assertThat(built.jobName()).startsWith("lc4j-batch-");
        assertThat(built.roleArn()).isEqualTo("arn:role");
        assertThat(built.modelId()).isEqualTo("model-x");
        assertThat(built.inputDataConfig().s3InputDataConfig().s3Uri()).startsWith("s3://in-bucket/in/lc4j-batch-");
        assertThat(built.inputDataConfig().s3InputDataConfig().s3InputFormat()).isEqualTo(S3InputFormat.JSONL);
        assertThat(built.outputDataConfig().s3OutputDataConfig().s3Uri()).isEqualTo("s3://out-bucket/out");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieve_completed_excludes_manifest_and_orders_by_record_id() {
        when(bedrock.getModelInvocationJob(any(Consumer.class)))
                .thenReturn(GetModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .status(ModelInvocationJobStatus.PARTIALLY_COMPLETED)
                        .build());
        when(s3.listObjectsV2(any(Consumer.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(
                                S3Object.builder()
                                        .key("out/job-123/manifest.json.out")
                                        .build(),
                                S3Object.builder()
                                        .key("out/job-123/input.jsonl.out")
                                        .build())
                        .build());
        String output = "{\"recordId\":\"r0000000001\",\"error\":{\"message\":\"boom\"}}\n"
                + "{\"recordId\":\"r0000000000\",\"modelOutput\":{\"output\":{\"message\":{\"content\":"
                + "[{\"text\":\"first\"}]}},\"stopReason\":\"end_turn\"}}\n";
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), output.getBytes(UTF_8)));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.SUCCEEDED);
        List<BatchItemResult<ChatResponse>> results = response.results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(0).response().aiMessage().text()).isEqualTo("first");
        assertThat(results.get(1).isSuccess()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieve_reads_and_merges_all_result_files_in_record_order() {
        when(bedrock.getModelInvocationJob(any(Consumer.class)))
                .thenReturn(GetModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .status(ModelInvocationJobStatus.COMPLETED)
                        .build());
        when(s3.listObjectsV2(any(Consumer.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(
                                S3Object.builder()
                                        .key("out/job-123/manifest.json.out")
                                        .build(),
                                S3Object.builder()
                                        .key("out/job-123/part1.jsonl.out")
                                        .build(),
                                S3Object.builder()
                                        .key("out/job-123/part2.jsonl.out")
                                        .build())
                        .build());
        String part1 = "{\"recordId\":\"r0000000002\",\"modelOutput\":{\"output\":{\"message\":{\"content\":"
                + "[{\"text\":\"third\"}]}},\"stopReason\":\"end_turn\"}}\n";
        String part2 = "{\"recordId\":\"r0000000000\",\"modelOutput\":{\"output\":{\"message\":{\"content\":"
                + "[{\"text\":\"first\"}]}},\"stopReason\":\"end_turn\"}}\n"
                + "{\"recordId\":\"r0000000001\",\"modelOutput\":{\"output\":{\"message\":{\"content\":"
                + "[{\"text\":\"second\"}]}},\"stopReason\":\"end_turn\"}}\n";
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), part1.getBytes(UTF_8)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), part2.getBytes(UTF_8)));

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        List<BatchItemResult<ChatResponse>> results = response.results();
        assertThat(results).hasSize(3);
        assertThat(results.get(0).response().aiMessage().text()).isEqualTo("first");
        assertThat(results.get(1).response().aiMessage().text()).isEqualTo("second");
        assertThat(results.get(2).response().aiMessage().text()).isEqualTo("third");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieve_running_returns_no_results() {
        when(bedrock.getModelInvocationJob(any(Consumer.class)))
                .thenReturn(GetModelInvocationJobResponse.builder()
                        .jobArn(JOB_ARN)
                        .status(ModelInvocationJobStatus.IN_PROGRESS)
                        .build());

        BatchResponse<ChatResponse> response = model().retrieve(JOB_ARN);

        assertThat(response.state()).isEqualTo(BatchState.RUNNING);
        assertThat(response.results()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancel_stops_the_job() {
        model().cancel(JOB_ARN);
        verify(bedrock).stopModelInvocationJob(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_maps_summaries_and_next_token() {
        when(bedrock.listModelInvocationJobs(any(Consumer.class)))
                .thenReturn(ListModelInvocationJobsResponse.builder()
                        .invocationJobSummaries(ModelInvocationJobSummary.builder()
                                .jobArn(JOB_ARN)
                                .status(ModelInvocationJobStatus.COMPLETED)
                                .build())
                        .nextToken("next")
                        .build());

        BatchPage<ChatResponse> page = model().list(new BatchPagination(10, null));

        assertThat(page.batches()).hasSize(1);
        assertThat(page.batches().get(0).batchId()).isEqualTo(JOB_ARN);
        assertThat(page.batches().get(0).state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(page.nextPageToken()).isEqualTo("next");
    }

    @Test
    void submit_rejects_tool_specifications() {
        BatchRequest<ChatRequest> request = new BatchRequest<>(List.of(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(ToolSpecification.builder().name("t").build())
                .build()));

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model().submit(request))
                .withMessageContaining("Tool calling is not supported");
    }

    @Test
    void maps_all_job_statuses_to_batch_states() {
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.SUBMITTED))
                .isEqualTo(BatchState.PENDING);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.VALIDATING))
                .isEqualTo(BatchState.PENDING);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.SCHEDULED))
                .isEqualTo(BatchState.PENDING);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.IN_PROGRESS))
                .isEqualTo(BatchState.RUNNING);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.STOPPING))
                .isEqualTo(BatchState.RUNNING);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.COMPLETED))
                .isEqualTo(BatchState.SUCCEEDED);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.PARTIALLY_COMPLETED))
                .isEqualTo(BatchState.SUCCEEDED);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.FAILED))
                .isEqualTo(BatchState.FAILED);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.STOPPED))
                .isEqualTo(BatchState.CANCELLED);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.EXPIRED))
                .isEqualTo(BatchState.EXPIRED);
        assertThat(BedrockBatchChatModel.toBatchState(ModelInvocationJobStatus.UNKNOWN_TO_SDK_VERSION))
                .isEqualTo(BatchState.UNSPECIFIED);
        assertThat(BedrockBatchChatModel.toBatchState(null)).isEqualTo(BatchState.UNSPECIFIED);
    }
}
