package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_ROLE_ARN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_BUCKET", matches = ".+")
class BedrockBatchChatModelIT {

    private static final int RECORD_COUNT = 100;

    private final Region region = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));
    private final String bucket = System.getenv("BEDROCK_BATCH_BUCKET");
    private final String roleArn = System.getenv("BEDROCK_BATCH_ROLE_ARN");
    private final String model =
            System.getenv().getOrDefault("BEDROCK_BATCH_MODEL", "anthropic.claude-3-haiku-20240307-v1:0");
    private final String prefix = "langchain4j-batch-it/" + UUID.randomUUID();

    private final S3Client s3 = S3Client.builder().region(region).build();

    private String unfinishedBatchId;

    private BedrockBatchChatModel model() {
        return BedrockBatchChatModel.builder()
                .region(region)
                .modelId(model)
                .roleArn(roleArn)
                .outputS3Uri("s3://" + bucket + "/" + prefix)
                .jobTimeout(Duration.ofHours(24))
                .defaultRequestParameters(DefaultChatRequestParameters.builder()
                        .maxOutputTokens(16)
                        .build())
                .build();
    }

    @Test
    void submits_polls_and_reads_results() throws Exception {
        List<ChatRequest> requests = new ArrayList<>();
        for (int i = 0; i < RECORD_COUNT; i++) {
            requests.add(ChatRequest.builder()
                    .messages(UserMessage.from("Reply with the single word: ok"))
                    .build());
        }

        BedrockBatchChatModel model = model();
        BatchResponse<ChatResponse> submitted = model.submit(new BatchRequest<>(requests));
        unfinishedBatchId = submitted.batchId();
        assertThat(submitted.batchId()).isNotBlank();
        assertThat(submitted.state()).isEqualTo(BatchState.PENDING);

        BatchResponse<ChatResponse> result = pollUntilTerminal(model, submitted.batchId());
        unfinishedBatchId = null;
        assertThat(result.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(result.results()).hasSize(RECORD_COUNT);
        assertThat(result.results().get(0).isSuccess()).isTrue();
        assertThat(result.results().get(0).response().aiMessage().text()).isNotBlank();
    }

    @Test
    void lists_jobs() {
        assertThat(model().list(null).batches()).isNotNull();
    }

    private BatchResponse<ChatResponse> pollUntilTerminal(BedrockBatchChatModel model, String batchId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + MINUTES.toMillis(60);
        while (System.currentTimeMillis() < deadline) {
            BatchResponse<ChatResponse> response = model.retrieve(batchId);
            BatchState state = response.state();
            if (state == BatchState.SUCCEEDED
                    || state == BatchState.FAILED
                    || state == BatchState.CANCELLED
                    || state == BatchState.EXPIRED) {
                return response;
            }
            MINUTES.sleep(1);
        }
        throw new AssertionError("Batch job did not complete within the timeout");
    }

    @AfterEach
    void cleanup() {
        if (unfinishedBatchId != null) {
            model().cancel(unfinishedBatchId);
        }
        List<ObjectIdentifier> objects = s3.listObjectsV2(b -> b.bucket(bucket).prefix(prefix)).contents().stream()
                .map(S3Object::key)
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        if (!objects.isEmpty()) {
            s3.deleteObjects(b -> b.bucket(bucket).delete(d -> d.objects(objects)));
        }
    }
}
